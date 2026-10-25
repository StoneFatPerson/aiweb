package com.aiweb.config;

import com.aiweb.entity.KnowledgePoint;
import com.aiweb.entity.Question;
import com.aiweb.mapper.KnowledgePointMapper;
import com.aiweb.mapper.QuestionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 题库与知识点的自动导入（多科目）。
 * 位置：src/main/java/com/aiweb/config/QuestionDataInitializer.java
 *
 * 作用：应用启动时扫描 data/ 目录下所有 *-seed-*.json（数学/英语/政治），
 *   - knowledge_point 表：按知识点 code 前缀（MATH-/ENGLISH-/POLITICS-）判断该科目是否已导入
 *   - question_bank 表：按 subject_code 判断该科目是否已导入
 *   只导「没有的」，已导入的跳过，不会重复。
 *
 * 好处：后续往 data/ 里加新批次文件（如 math-seed-batch2.json）无需改代码；
 *       老库（只导过数学）启动时会自动补齐英语、政治，不影响已有数据。
 *
 * 数据来源：项目原创编写（非真题），各 JSON 的 scopeNote 已注明。
 */
@Component
public class QuestionDataInitializer implements ApplicationRunner {

    /** data 目录下的题库文件命名规则：{科目}-seed-batch{N}.json */
    private static final String DATA_PREFIX = "data/";
    private static final String[] SEED_FILES = {
            "math-seed-batch1.json",
            "english-seed-batch1.json",
            "politics-seed-batch1.json",
            "math-seed-batch2.json",
            "english-seed-batch2.json",
            "politics-seed-batch2.json"
    };

    private final QuestionMapper questionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ObjectMapper objectMapper;

    public QuestionDataInitializer(QuestionMapper questionMapper,
                                   KnowledgePointMapper knowledgePointMapper,
                                   ObjectMapper objectMapper) {
        this.questionMapper = questionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String file : SEED_FILES) {
            try {
                Resource res = new ClassPathResource(DATA_PREFIX + file);
                if (!res.exists()) {
                    System.out.println("[题库初始化] 未找到 " + file + "，跳过。");
                    continue;
                }
                importFile(file, res);
            } catch (Exception e) {
                System.err.println("[题库初始化] 导入 " + file + " 失败：" + e.getMessage());
            }
        }
    }

    /** 导入单个题库文件（按科目幂等：该科目已有数据则跳过） */
    private void importFile(String file, Resource res) throws Exception {
        JsonNode root;
        try (InputStream in = res.getInputStream()) {
            root = objectMapper.readTree(in);
        }
        String subjectCode = root.path("subject").path("code").asText("unknown");

        // 1) 导入知识点：按「知识点 code」逐条幂等（已存在则跳过，不重复导入）
        if (root.has("chapters")) {
            Map<String, String> chapterAppl = new HashMap<>();
            int kpCount = 0, kpSkip = 0;
            for (JsonNode ch : root.get("chapters")) {
                String chCode = ch.get("code").asText();
                JsonNode appl = ch.get("applicableTo");
                String applStr = appl == null ? "" :
                        StreamSupport.stream(appl.spliterator(), false)
                                .map(JsonNode::asText).collect(Collectors.joining(","));
                chapterAppl.put(chCode, applStr);

                JsonNode points = ch.get("points");
                if (points != null) {
                    for (JsonNode p : points) {
                        String code = p.get("code").asText();
                        long exists = knowledgePointMapper.selectCount(
                                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getCode, code));
                        if (exists > 0) { kpSkip++; continue; }
                        KnowledgePoint kp = new KnowledgePoint();
                        kp.setCode(code);
                        kp.setName(p.get("name").asText());
                        kp.setChapterCode(chCode);
                        kp.setChapterName(ch.get("name").asText());
                        kp.setApplicableTo(applStr);
                        knowledgePointMapper.insert(kp);
                        kpCount++;
                    }
                }
            }
            System.out.println("[题库初始化] " + file + "：导入知识点 " + kpCount + " 个，跳过已存在 " + kpSkip + " 个（" + subjectCode + "）。");
        }

        // 2) 导入题目：按「题目 code」逐条幂等（已存在则跳过，支持分批新增而不重复）
        if (root.has("questions")) {
            // 章节 -> 适用类型 映射（优先用题目自带 applicableTo，回退到章节映射）
            Map<String, String> chapterAppl = new HashMap<>();
            if (root.has("chapters")) {
                for (JsonNode ch : root.get("chapters")) {
                    JsonNode appl = ch.get("applicableTo");
                    String applStr = appl == null ? "" :
                            StreamSupport.stream(appl.spliterator(), false)
                                    .map(JsonNode::asText).collect(Collectors.joining(","));
                    chapterAppl.put(ch.get("code").asText(), applStr);
                }
            }

            int qCount = 0, qSkip = 0;
            for (JsonNode qn : root.get("questions")) {
                String code = qn.path("code").asText();
                try {
                    Question existed = questionMapper.selectOne(
                            new LambdaQueryWrapper<Question>().eq(Question::getCode, code));
                    if (existed != null) { qSkip++; continue; }

                    Question q = new Question();
                    q.setCode(code);
                    // subjectCode 缺失时回退到文件级 subject。
                    // 教训：之前某题的 subjectCode 漏写，qn.get(...) 返回 null，
                    // 后续 .asText() 抛 NPE，直接把整批导入中断——该题及之后的题全部静默丢失。
                    q.setSubjectCode(qn.has("subjectCode") && !qn.get("subjectCode").isNull()
                            ? qn.get("subjectCode").asText() : subjectCode);
                    q.setChapterCode(qn.has("chapterCode") && !qn.get("chapterCode").isNull()
                            ? qn.get("chapterCode").asText() : null);
                    q.setPointCodes(joinArray(qn.get("pointCodes")));
                    // 优先用题目显式写的 applicableTo；没写则按所在章节推断
                    String appl = qn.has("applicableTo") && !qn.get("applicableTo").isNull()
                            ? qn.get("applicableTo").asText() : "";
                    q.setApplicableTo(appl.isBlank() ? chapterAppl.getOrDefault(qn.path("chapterCode").asText(), "") : appl);
                    q.setType(qn.get("type").asText());
                    q.setDifficulty(qn.path("difficulty").asInt());
                    q.setYear(qn.has("year") && !qn.get("year").isNull() ? qn.get("year").asText() : null);
                    q.setSource(qn.has("source") ? qn.get("source").asText() : "ORIGINAL_SEED");
                    q.setStem(qn.get("stem").asText());
                    q.setOptions(qn.has("options") ? qn.get("options").toString() : null);
                    q.setAnswer(qn.get("answer").asText());
                    q.setExplanation(qn.has("explanation") ? qn.get("explanation").asText() : "");
                    q.setKeywords(joinArray(qn.get("keywords")));
                    q.setVerified(1); // 种子题为本项目原创，已核对
                    questionMapper.insert(q);
                    qCount++;
                } catch (Exception e) {
                    // 单题失败只跳过该题并明确报错，绝不中断整批（否则会像之前那样静默丢题）
                    System.out.println("[题库初始化] 跳过异常题目 " + code + "：" + e);
                }
            }
            System.out.println("[题库初始化] " + file + "：导入题目 " + qCount + " 道，跳过已存在 " + qSkip + " 道（" + subjectCode + "）。");
        }
    }

    /** 科目代码 -> 知识点 code 前缀（math -> MATH-，english -> ENGLISH-，politics -> POLITICS-） */
    private String prefixOf(String subjectCode) {
        return switch (subjectCode) {
            case "math" -> "MATH-";
            case "english" -> "ENGLISH-";
            case "politics" -> "POLITICS-";
            default -> subjectCode.toUpperCase() + "-";
        };
    }

    private String joinArray(JsonNode arr) {
        if (arr == null || !arr.isArray()) return "";
        return StreamSupport.stream(arr.spliterator(), false)
                .map(JsonNode::asText).collect(Collectors.joining(","));
    }
}
