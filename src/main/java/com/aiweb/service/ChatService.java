package com.aiweb.service;

import com.aiweb.entity.ChatMessage;
import com.aiweb.mapper.ChatMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 对话的核心业务逻辑。
 * 位置：src/main/java/com/aiweb/service/ChatService.java
 * 作用：接收用户问题 -> 带上历史上下文 -> 调用智谱 GLM -> 把问答存进数据库。
 */
@Service
public class ChatService {

    /** 系统提示词：定义 AI 的角色。改这一句就能改变 AI 的人设 */
    private static final String SYSTEM_PROMPT = "你是一个专业、友好的中文 AI 助手，回答简洁清晰。";

    /** 最多携带多少条历史消息（防止超出模型上下文长度） */
    private static final int HISTORY_LIMIT = 10;

    private final ChatClient chatClient;
    private final ChatMessageMapper chatMessageMapper;

    /**
     * ChatClient.Builder 由 Spring AI 自动创建，直接注入即可。
     * 它读取 application.yml 里的 spring.ai.openai.* 配置。
     */
    public ChatService(ChatClient.Builder builder, ChatMessageMapper chatMessageMapper) {
        this.chatClient = builder.build();
        this.chatMessageMapper = chatMessageMapper;
    }

    /** 普通对话：等 AI 全部说完再一次性返回 */
    public String chat(String sessionId, String userMessage) {
        saveMessage(sessionId, "user", userMessage);

        String answer = chatClient.prompt()
                .messages(buildHistory(sessionId, userMessage))
                .call()
                .content();

        saveMessage(sessionId, "assistant", answer);
        return answer;
    }

    /** 流式对话：AI 说一个字返回一个字，前端可以打字机效果显示 */
    public Flux<String> chatStream(String sessionId, String userMessage) {
        saveMessage(sessionId, "user", userMessage);

        AtomicReference<StringBuilder> buffer = new AtomicReference<>(new StringBuilder());

        return chatClient.prompt()
                .messages(buildHistory(sessionId, userMessage))
                .stream()
                .content()
                .doOnNext(buffer.get()::append)
                .doOnComplete(() -> saveMessage(sessionId, "assistant", buffer.get().toString()));
    }

    /** 查询某个会话的历史记录 */
    public List<ChatMessage> history(String sessionId) {
        LambdaQueryWrapper<ChatMessage> query = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime)
                .last("LIMIT 50");
        return chatMessageMapper.selectList(query);
    }

    /** 组装上下文：系统提示词 + 最近若干轮历史 + 本次提问 */
    private List<Message> buildHistory(String sessionId, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        LambdaQueryWrapper<ChatMessage> query = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT " + HISTORY_LIMIT);
        List<ChatMessage> rows = chatMessageMapper.selectList(query);
        Collections.reverse(rows);

        for (ChatMessage row : rows) {
            if ("user".equals(row.getRole())) {
                messages.add(new UserMessage(row.getContent()));
            } else {
                messages.add(new AssistantMessage(row.getContent()));
            }
        }
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    /** 把一条消息存进数据库 */
    private void saveMessage(String sessionId, String role, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        chatMessageMapper.insert(message);
    }
}
