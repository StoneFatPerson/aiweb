package com.aiweb.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 检索增强生成服务（认知科普问答的核心）。
 * 位置：src/main/java/com/aiweb/service/RagService.java
 * 作用：用户提问 → 向量库找出"语义最相近"的几段考研资料 → 把资料作为【参考资料】塞给大模型
 *       → 要求模型"只依据资料作答、不许瞎编"。这正是我们定的原则：事实进库、模型只组织语言。
 */
@Service
public class RagService {

    /** 相似度低于这个值就视为"资料里没有相关内容"，直接兜底，避免模型编造 */
    private static final double SCORE_THRESHOLD = 0.3;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    public RagAnswer ask(String question) {
        if (question == null || question.trim().isEmpty()) {
            return new RagAnswer("请输入你的问题，例如：学硕和专硕有什么区别？", List.of());
        }

        // 1) 检索：从向量库找语义最相近的 4 段资料
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(4).build());

        // 2) 防幻觉：没命中，或最相关那段都不够"像"，就如实说不知道
        if (docs.isEmpty() || docs.get(0).getScore() < SCORE_THRESHOLD) {
            return new RagAnswer(
                    "抱歉，我的考研知识库里暂时没有相关内容。考试政策、招生信息请以中国研究生招生信息网" +
                            "（研招网）和目标院校研究生院官网的官方公告为准。",
                    List.of());
        }

        // 3) 组装参考资料文本
        String context = docs.stream()
                .map(d -> "【" + d.getMetadata().getOrDefault("title", "资料") + "】\n" + d.getText())
                .collect(Collectors.joining("\n\n"));

        String system = "你是考研科普助手。请严格只依据下方【参考资料】回答用户问题，" +
                "不要使用资料以外的知识，也不要编造任何数据或政策。如果参考资料不足以回答，" +
                "请明确说明「资料中没有相关信息」。回答要简洁、准确、有条理。";

        // 4) 生成：把资料和问题一起发给大模型，模型只负责"用地道的语言组织答案"
        //    大模型（免费档）容易限流，调用失败或返回空时给出友好提示，而不是抛 500。
        List<String> sources = docs.stream()
                .map(d -> (String) d.getMetadata().getOrDefault("title", ""))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        String answer;
        try {
            answer = chatClient.prompt()
                    .system(system)
                    .user("【参考资料】\n" + context + "\n\n【用户问题】" + question)
                    .call()
                    .content();
        } catch (Exception e) {
            answer = "（生成失败：大模型接口暂时不可用，可能是免费档限流，请稍后重试。下方「参考来源」是已检索到的相关资料。）";
            return new RagAnswer(answer, sources);
        }
        if (answer == null || answer.isBlank()) {
            answer = "（模型暂时没有返回内容，可能是免费接口限流，请稍后重试。）";
        }

        return new RagAnswer(answer, sources);
    }

    /** 问答结果：答案文本 + 命中的参考来源标题 */
    public static class RagAnswer {
        public final String answer;
        public final List<String> sources;

        public RagAnswer(String answer, List<String> sources) {
            this.answer = answer;
            this.sources = sources;
        }
    }
}
