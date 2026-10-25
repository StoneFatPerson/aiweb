package com.aiweb.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理：把服务层的业务异常翻译成合理的 HTTP 状态码与统一错误结构。
 * 位置：src/main/java/com/aiweb/controller/GlobalExceptionHandler.java
 *
 * 为什么需要：之前 IllegalArgumentException（如「题干不能为空」「缺少学生标识」）
 * 直接冒到容器层变成 500，前端只能看到「Request failed with status code 500」，
 * 用户完全不知道哪错了。这里统一成：
 *   - IllegalArgumentException → 400（请求有问题，消息可直接展示给用户）
 *   - DuplicateKeyException    → 409（数据冲突，如错题本唯一键）
 *   - 其他                     → 500（服务端问题，只给通用文案，细节进日志，不泄露内部信息）
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 用 SLF4J 记录异常，而不是 e.printStackTrace()。
     * 区别：printStackTrace 只把堆栈打到控制台，不带时间、不带级别，
     * 无法被日志框架（文件/滚动/采集）接管；log.error 会按 application.yml 的
     * logging 配置落到日志文件，带时间戳与完整堆栈，线上排查才有据可查。
     */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 参数/业务校验失败：400，消息体可直接展示给用户 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return build(400, e.getMessage());
    }

    /**
     * 缺少必填请求参数（如 /api/wrongbook 不传 studentId）：400。
     * 必须显式处理——否则会被下面的通用 Exception 分支吞掉变成 500，
     * 「缺参数」明明是客户端问题，不该让用户以为服务器炸了。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return build(400, "缺少必填参数：" + e.getParameterName());
    }

    /** 数据冲突（如并发双提交撞唯一键）：409 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateKeyException e) {
        return build(409, "数据冲突，请稍后重试");
    }

    /** 兜底：500，只给通用文案（细节只进日志，不给前端，避免泄露内部信息） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("未处理的服务器异常", e);
        return build(500, "服务器开小差了，请稍后重试；如持续出现请查看后端日志");
    }

    private static ResponseEntity<Map<String, Object>> build(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
