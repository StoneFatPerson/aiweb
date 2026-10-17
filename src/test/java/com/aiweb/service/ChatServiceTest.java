package com.aiweb.service;

import com.aiweb.mapper.ChatMessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * AI 对话服务的兜底测试。
 * 位置：src/test/java/com/aiweb/service/ChatServiceTest.java
 *
 * 测什么：**项目核心承诺——大模型限流/故障时接口不许 500，必须降级为友好文案。**
 * ChatService 曾是全项目唯一漏掉 try-catch 的 AI 调用点（免费档一限流就 500），
 * 这个测试就是防止回归的哨兵。
 */
class ChatServiceTest {

    private ChatService service;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(Mockito.mock(ChatClient.class));
        service = new ChatService(builder, mock(ChatMessageMapper.class));
    }

    @Test
    @DisplayName("模型限流/故障 → 返回友好兜底文案，不抛异常（核心承诺）")
    void modelFailureReturnsFallbackText() {
        // 造一个「模型一调就炸」的客户端
        ChatClient broken = Mockito.mock(ChatClient.class);
        when(broken.prompt()).thenThrow(new RuntimeException("rate limited 429"));
        ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(broken);
        ChatService brokenService = new ChatService(builder, mock(ChatMessageMapper.class));

        String answer = brokenService.chat("session-1", "你好");
        assertNotNull(answer);
        assertTrue(answer.contains("暂时不可用"), "应返回降级文案，实际：" + answer);
        assertTrue(answer.contains("稍后重试"));
    }

    @Test
    @DisplayName("模型返回空内容 → 也有兜底文案")
    void emptyContentHasFallback() {
        ChatClient empty = Mockito.mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = Mockito.mock(ChatClient.CallResponseSpec.class);
        when(empty.prompt()).thenReturn(spec);
        when(spec.messages(anyList())).thenReturn(spec);
        when(spec.call()).thenReturn(call);
        when(call.content()).thenReturn("   "); // 空白内容
        ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(empty);
        ChatService emptyService = new ChatService(builder, mock(ChatMessageMapper.class));

        String answer = emptyService.chat("session-2", "在吗");
        assertTrue(answer.contains("没有返回内容"), "空白返回也应有兜底，实际：" + answer);
    }

    @Test
    @DisplayName("模型正常 → 原样返回模型内容")
    void modelSuccessReturnsContent() {
        ChatClient ok = Mockito.mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = Mockito.mock(ChatClient.CallResponseSpec.class);
        when(ok.prompt()).thenReturn(spec);
        when(spec.messages(anyList())).thenReturn(spec);
        when(spec.call()).thenReturn(call);
        when(call.content()).thenReturn("同学你好，这是正常回答");
        ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(ok);
        ChatService okService = new ChatService(builder, mock(ChatMessageMapper.class));

        assertEquals("同学你好，这是正常回答", okService.chat("session-3", "你好"));
    }
}
