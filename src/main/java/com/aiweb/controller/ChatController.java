package com.aiweb.controller;

import com.aiweb.entity.ChatMessage;
import com.aiweb.service.ChatService;
import com.aiweb.service.CollegeService;
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
 *   GET  /api/health          健康检查（含数据库连通性探测）
 *   POST /api/chat            普通对话（等 AI 说完一起返回）
 *   GET  /api/chat/stream     流式对话（打字机效果）
 *   GET  /api/history         查询历史消息
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;
    private final CollegeService collegeService;

    public ChatController(ChatService chatService, CollegeService collegeService) {
        this.chatService = chatService;
        this.collegeService = collegeService;
    }

    /**
     * 健康检查：浏览器打开 http://localhost:8080/api/health 看到 ok 就说明服务正常。
     *
     * 除了「服务活着」，还探测数据库是否真的可用（执行一条最轻的 count 查询）——
     * 因为「Tomcat 起来了但数据库连不上」时页面所有接口都会 500，
     * 而日志里的原因（Access denied / connection refused）用户不一定看。
     * 这里把数据库状态也报出来，前端就能显示明确提示，而不是泛泛的 500。
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("model", "glm-4.7-flash");
        try {
            long collegeCount = collegeService.countColleges();
            result.put("status", "ok");
            result.put("database", "ok");
            result.put("collegeCount", collegeCount);
        } catch (Exception e) {
            // 数据库不可用：HTTP 503 + 明确原因，前端健康检查失败会提示，业务接口也不会再各自 500 让人看不懂
            result.put("status", "error");
            result.put("database", "unavailable");
            result.put("message", "数据库连接失败：请确认 MySQL 正在运行，且环境变量 DB_PASSWORD 与你的 MySQL 密码一致"
                    + "（改完要重启 IDEA / CMD）。原始错误：" + e.getMessage());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, (String) result.get("message"));
        }
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
