package com.aiweb.controller;

import com.aiweb.agent.AgentOrchestrator;
import com.aiweb.agent.AgentResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 规划助手（Agent 编排）接口。
 * 位置：src/main/java/com/aiweb/controller/AgentController.java
 * 作用：POST /api/agent/run 输入目标，返回汇总答案 + 每一步执行轨迹 + 参考来源。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentOrchestrator orchestrator;

    public AgentController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * 运行一次 Agent 编排。
     * 入参：{ goal: 目标描述, studentId?: 学号（提供则结合个人学情） }
     * 返回：{ goal, answer, degraded, steps: [{index,name,status,durationMs,output}], sources }
     */
    @PostMapping("/run")
    public AgentResult run(@RequestBody AgentRunRequest body) {
        String goal = (body == null || body.goal() == null) ? "" : body.goal();
        String studentId = (body == null) ? null : body.studentId();
        return orchestrator.run(goal, studentId);
    }

    /** 运行请求体 */
    public record AgentRunRequest(String goal, String studentId) {
    }
}
