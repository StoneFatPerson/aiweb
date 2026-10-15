package com.aiweb.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 检索增强生成服务（认知科普模块的核心）。
 * 位置：src/main/java/com/aiweb/service/RagService.java
 *
 * 两种用法：
 * 1) ask(question)        —— 旧版「问答」：用户提问，模型只依据资料作答。
 * 2) explain(topicKey)    —— 新版「主动说明式讲解」：系统按主题去知识库检索相关资料，
 *                            让大模型把资料组织成一段连贯、通俗的说明性文章（非一问一答），
 *                            主动呈现给考研学生阅读。这正是我们定的原则：事实进库、模型只组织语言。
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

    /* ===================== 主动说明式讲解 ===================== */

    /** 认知科普主题：前端展示用，每个主题带一个用于向量检索的代表性查询 */
    public static class Topic {
        public final String key;
        public final String title;
        public final String query;

        public Topic(String key, String title, String query) {
            this.key = key;
            this.title = title;
            this.query = query;
        }
    }

    /** 预置的科普主题（覆盖术语词典、学硕专硕、时间线，以及流程/科目/院校/建议） */
    private static final List<Topic> TOPICS = List.of(
        new Topic("terminology", "考研专业术语词典",
            "考研常见专业术语名词解释 学硕专硕 数学一二三 英语一二 A区B区 国家线院校线自划线 全日制非全 推免 调剂 报录比 统考联考 初试复试"),
        new Topic("xueshuo", "学术硕士与专业硕士的区别",
            "学术硕士和专业硕士的区别 培养目标 学制 课程 就业方向 考试难度 读博 学费 奖学金"),
        new Topic("timeline", "考研完整时间线",
            "考研时间线 备考启动 复习 大纲 招生简章 报名 网上确认 初试 查分 国家线 复试 调剂 拟录取 录取通知书 入学"),
        new Topic("flow", "考研整体流程",
            "考研整体流程 初试 复试 调剂 录取 流程步骤"),
        new Topic("exam", "考研考试科目",
            "考研考试科目 思想政治理论 英语 数学 专业课 考什么"),
        new Topic("shandong", "山东考研院校",
            "山东 考研院校 山东大学 中国海洋大学 中国石油大学 山东师范大学 青岛大学 层次 特色"),
        new Topic("advice", "备考建议",
            "考研备考建议 择校 复习节奏 全年规划")
    );

    /** 返回主题列表（含 key 与 title），供前端渲染左侧目录 */
    public List<Topic> topics() {
        return TOPICS;
    }

    /** 主动说明式讲解结果：标题 + 连贯文章 + 参考来源 */
    public static class TopicExplain {
        public final String title;
        public final String content;
        public final List<String> sources;

        public TopicExplain(String title, String content, List<String> sources) {
            this.title = title;
            this.content = content;
            this.sources = sources;
        }
    }

    /**
     * 主动说明式讲解：给定主题 key，检索相关资料并让大模型合成连贯的说明性文章。
     * 未知 key 时把输入当自由查询，但仍用"说明式"而非"问答式"生成。
     */
    public TopicExplain explain(String topicKey) {
        Topic topic = TOPICS.stream()
                .filter(t -> t.key.equals(topicKey))
                .findFirst()
                .orElse(null);

        final String title;
        final String query;
        if (topic == null) {
            // 未知主题：把输入本身当作检索查询，标题用兜底文案
            query = (topicKey == null) ? "" : topicKey.trim();
            title = "考研科普";
        } else {
            query = topic.query;
            title = topic.title;
        }

        if (query.isEmpty()) {
            return new TopicExplain(title, "请选择一个讲解主题。", List.of());
        }

        // 1) 检索：从向量库找语义最相近的若干段资料（讲解需要更丰富的上下文，取 10 条）
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(10).build());

        // 2) 防幻觉：没命中或最相关那段都不够"像"，如实说不知道
        if (docs.isEmpty() || docs.get(0).getScore() < SCORE_THRESHOLD) {
            return new TopicExplain(title,
                    "抱歉，我的考研知识库里暂时没有该主题的充足资料。考试政策、招生信息请以中国研究生" +
                            "招生信息网（研招网）和目标院校研究生院官网的官方公告为准。",
                    List.of());
        }

        // 3) 组装参考资料文本 + 参考来源标题（去重）
        String context = docs.stream()
                .map(d -> "【" + d.getMetadata().getOrDefault("title", "资料") + "】\n" + d.getText())
                .collect(Collectors.joining("\n\n"));
        List<String> sources = docs.stream()
                .map(d -> (String) d.getMetadata().getOrDefault("title", ""))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream().collect(Collectors.toList());

        // 4) 生成：要求模型写"连贯、通俗的说明性文章"，明确禁止一问一答形式
        String system = "你是考研科普编辑。请严格只依据下方【参考资料】，撰写一段连贯、通俗易懂的说明性文章，" +
                "向准备考研的本科生系统讲解「" + title + "」。要求：使用段落与必要的小标题组织内容，条理清晰；" +
                "只使用资料中的事实，不编造任何数据或政策；语言亲切易懂，适合本科生阅读；" +
                "不要使用问答（一问一答）形式，而是直接讲解。";

        String content;
        try {
            content = chatClient.prompt()
                    .system(system)
                    .user("【参考资料】\n" + context + "\n\n请撰写关于「" + title + "」的说明性讲解文章。")
                    .call()
                    .content();
        } catch (Exception e) {
            // 大模型（免费档）限流时降级：直接拼接知识库原文，保证页面有可读内容
            content = "（大模型接口暂时不可用，以下为知识库相关资料原文，稍后可重试获取智能讲解。）\n\n"
                    + docs.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
            return new TopicExplain(title, content, sources);
        }
        if (content == null || content.isBlank()) {
            content = "（模型暂时没有返回内容，可能是免费接口限流，请稍后重试。）";
        }

        return new TopicExplain(title, content, sources);
    }

    /* ===================== 旧版问答（保留，兼容历史调用） ===================== */

    public RagAnswer ask(String question) {
        if (question == null || question.trim().isEmpty()) {
            return new RagAnswer("请输入你的问题，例如：学硕和专硕有什么区别？", List.of());
        }

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(4).build());

        if (docs.isEmpty() || docs.get(0).getScore() < SCORE_THRESHOLD) {
            return new RagAnswer(
                    "抱歉，我的考研知识库里暂时没有相关内容。考试政策、招生信息请以中国研究生招生信息网" +
                            "（研招网）和目标院校研究生院官网的官方公告为准。",
                    List.of());
        }

        String context = docs.stream()
                .map(d -> "【" + d.getMetadata().getOrDefault("title", "资料") + "】\n" + d.getText())
                .collect(Collectors.joining("\n\n"));

        String system = "你是考研科普助手。请严格只依据下方【参考资料】回答用户问题，" +
                "不要使用资料以外的知识，也不要编造任何数据或政策。如果参考资料不足以回答，" +
                "请明确说明「资料中没有相关信息」。回答要简洁、准确、有条理。";

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

    /* ===================== 会话式答疑（右侧 AI 窗口） ===================== */

    /** 对话中的一轮：role 为 "user" 或 "assistant"，content 为该轮文本 */
    public static class ChatTurn {
        public final String role;
        public final String content;

        public ChatTurn(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    /**
     * 会话式答疑：在 RAG 检索的基础上，结合对话历史理解用户的"追问"。
     *
     * @param history 之前若干轮对话（不含当前问题），用于追问上下文
     * @param question 本次提问
     * @param context 用户当前正在阅读的主题标题（可选，帮助 AI 理解背景）
     */
    public RagAnswer chat(List<ChatTurn> history, String question, String context) {
        if (question == null || question.trim().isEmpty()) {
            return new RagAnswer("请输入你的问题，例如：学硕和专硕有什么区别？", List.of());
        }

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(4).build());

        if (docs.isEmpty() || docs.get(0).getScore() < SCORE_THRESHOLD) {
            return new RagAnswer(
                    "抱歉，我的考研知识库里暂时没有相关内容。考试政策、招生信息请以中国研究生招生信息网" +
                            "（研招网）和目标院校研究生院官网的官方公告为准。",
                    List.of());
        }

        String contextText = docs.stream()
                .map(d -> "【" + d.getMetadata().getOrDefault("title", "资料") + "】\n" + d.getText())
                .collect(Collectors.joining("\n\n"));
        List<String> sources = docs.stream()
                .map(d -> (String) d.getMetadata().getOrDefault("title", ""))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream().collect(Collectors.toList());

        // 把之前的对话整理成可读文本，帮助模型理解"追问"
        String historyText = "";
        if (history != null) {
            historyText = history.stream()
                    .map(h -> ("user".equals(h.role) ? "用户" : "助手") + "：" + h.content)
                    .collect(Collectors.joining("\n"));
        }
        String contextHint = (context == null || context.isBlank()) ? "" : ("用户当前正在阅读的主题是「" + context + "」。\n");

        String system = "你是考研科普答疑助手。请严格只依据下方【参考资料】回答用户问题，并可结合【对话历史】理解用户意图；" +
                "不要使用资料以外的知识，也不要编造任何数据或政策。如果参考资料不足以回答，" +
                "请明确说明「资料中没有相关信息」。回答要简洁、准确、有条理。";

        String userMsg = contextHint +
                "【对话历史】\n" + (historyText.isEmpty() ? "(无)" : historyText) +
                "\n\n【参考资料】\n" + contextText +
                "\n\n【用户问题】" + question;

        String answer;
        try {
            answer = chatClient.prompt()
                    .system(system)
                    .user(userMsg)
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
}
