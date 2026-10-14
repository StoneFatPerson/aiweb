package com.aiweb.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 答案图片 OCR 识别服务（视觉多模态）。
 * 位置：src/main/java/com/aiweb/service/OcrService.java
 *
 * 为什么不用 glm-4.7-flash？
 *   glm-4.7-flash 是「纯文本」模型，不能接收图片（已核实）。
 *   智谱在同一 ZHIPU_API_KEY、同一 OpenAI 兼容端点下提供免费的视觉模型
 *   glm-4v-flash，专门看图。我们复用已有的 ChatClient.Builder（base-url/密钥都已配好），
 *   只在每次请求时用 ChatOptions 把模型临时切到 glm-4v-flash，无需任何新依赖、新密钥。
 *
 * 职责：把用户上传的「手写/印刷答案图片」识别成文字与数学符号，
 *       并和该题的标准答案比较，给出 {识别内容, 是否等价}。
 */
@Service
public class OcrService {

    /** 智谱免费视觉模型，用于 OCR + 答案比对 */
    private static final String VISION_MODEL = "glm-4v-flash";

    private final ChatClient visionClient;

    public OcrService(ChatClient.Builder builder) {
        // builder 已带好 base-url 与 api-key，这里只 build 出客户端，模型在每次调用时切换
        this.visionClient = builder.build();
    }

    /**
     * 识别一张答案图片并判断是否与标准答案等价。
     *
     * @param image         图片原始字节（前端压缩后的 jpeg/png）
     * @param mimeType      图片 MIME，如 image/jpeg；为空时按 image/png 处理
     * @param correctAnswer 该题的标准答案（填空题为数学表达式，选择题为字母）
     * @param questionType  题型：FILL_BLANK / SINGLE_CHOICE
     * @return 识别结果与判定
     */
    public OcrResult recognize(byte[] image, String mimeType, String correctAnswer, String questionType) {
        if (image == null || image.length == 0) {
            return new OcrResult("", false, "图片为空");
        }
        String resourceMime = (mimeType == null || mimeType.isBlank()) ? "image/png" : mimeType;
        ByteArrayResource res = new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return "answer.png";
            }
        };

        boolean isChoice = "SINGLE_CHOICE".equals(questionType);
        String sys = "你是考研数学答题卡的阅卷 OCR 助手。请仔细识别用户上传图片中所写的【最终答案】，" +
                "可能是手写或印刷体，包含中文、数字、字母、上下标、根号、分数、希腊字母等数学符号。" +
                "请尽量保持原样输出，例如 y²+2xy、x²-1、1/2、∫xdx 等。";

        String user = "这道题的标准答案是：" + (correctAnswer == null ? "" : correctAnswer) + "\n" +
                "题型：" + (isChoice ? "单选题（答案应为一个字母，如 A/B/C/D）" : "填空题（一个数学表达式）") + "\n\n" +
                "请完成两件事：\n" +
                "1) 识别图片中的答案内容；\n" +
                "2) 判断图片答案与标准答案在数学上是否等价（选择题只看字母是否一致）。\n" +
                "只返回一个 JSON 对象，不要包含任何额外文字、不要使用 markdown 代码块包裹，格式严格如下：\n" +
                "{\"answer\":\"<识别到的答案原文>\",\"correct\":true或false}";

        try {
            String resp = visionClient.prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(VISION_MODEL)
                            .temperature(0.1)
                            .build())
                    .system(sys)
                    .user(u -> u.text(user).media(MimeTypeUtils.parseMimeType(resourceMime), res))
                    .call()
                    .content();
            return parse(resp);
        } catch (Exception e) {
            // 视觉模型限流或故障时：不拖垮主流程，返回错误，前端提示改用文字输入
            return new OcrResult("", false, "OCR 调用失败：" + e.getMessage());
        }
    }

    /** 从模型返回的字符串里稳妥地取出 answer / correct 两个字段 */
    private OcrResult parse(String raw) {
        if (raw == null) return new OcrResult("", false, "OCR 无返回");
        String s = raw.trim();
        // 去掉可能的 ```json ... ``` 包裹
        if (s.startsWith("```")) {
            s = s.replaceAll("^```[a-zA-Z]*", "").replaceAll("```$", "").trim();
        }
        Matcher block = Pattern.compile("\\{.*\\}", Pattern.DOTALL).matcher(s);
        if (block.find()) s = block.group();

        try {
            String answer = extractField(s, "answer");
            String correctStr = extractField(s, "correct");
            boolean correct = "true".equalsIgnoreCase(correctStr) || "1".equals(correctStr);
            return new OcrResult(answer, correct, null);
        } catch (Exception e) {
            // 解析失败：把整段返回当作识别内容，正确性交由 AssessmentService 的安全兜底再判一次
            return new OcrResult(raw, false, "OCR 结果解析失败，请改用文字输入或重拍");
        }
    }

    /** 简化的 JSON 字段提取，避免引入严格类型绑定带来的脆弱性 */
    private String extractField(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\"([^\"]*)\"|true|false|\\d+)")
                .matcher(json);
        if (m.find()) {
            String g = m.group(1);
            if (g.startsWith("\"")) return g.substring(1, g.length() - 1);
            return g;
        }
        return "";
    }

    /** OCR 识别结果 */
    public record OcrResult(String answer, boolean correct, String error) {
    }
}
