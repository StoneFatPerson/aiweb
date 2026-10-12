package com.aiweb.controller;

import com.aiweb.entity.ChatMessage;
import com.aiweb.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 对外提供的接口（前端页面通过这些地址来调用）。
 * 位置：src/main/java/com/aiweb/controller/ChatController.java
 *
 * 接口一览：
 *   GET  /api/health          健康检查，验证服务是否活着
 *   POST /api/chat            普通对话（等 AI 说完一起返回）
 *   GET  /api/chat/stream     流式对话（打字机效果）
 *   GET  /api/history         查询历史消息
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 健康检查：浏览器打开 http://localhost:8080/api/health 看到 ok 就说明服务正常 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("model", "glm-4.7-flash");
        return result;
    }

    /** 普通对话 */
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        String sessionId = defaultSessionId(request.sessionId());
        try {
            String answer = chatService.chat(sessionId, request.message());
            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", sessionId);
            result.put("answer", answer);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", sessionId);
            result.put("error", "调用大模型失败：" + e.getMessage());
            return result;
        }
    }

    /** 流式对话，返回 text/event-stream，前端可以边收边显示 */
    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(@RequestParam String message,
                                   @RequestParam(required = false) String sessionId) {
        return chatService.chatStream(defaultSessionId(sessionId), message);
    }

    /** 查询历史 */
    @GetMapping("/history")
    public List<ChatMessage> history(@RequestParam String sessionId) {
        return chatService.history(sessionId);
    }

    /** 前端没传会话 ID 时，自动生成一个 */
    private String defaultSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
    }

    /** 前端 POST 过来 JSON 的结构：{"sessionId":"xxx","message":"你好"} */
    public record ChatRequest(String sessionId, String message) {
    }
}
