package com.aiweb.agent;

import java.util.List;

/**
 * Agent 一次运行的完整结果：汇总答案 + 每一步执行轨迹 + 参考来源。
 * 位置：src/main/java/com/aiweb/agent/AgentResult.java
 */
public record AgentResult(
        String goal,
        String answer,
        boolean degraded,   // true 表示大模型不可用、已用规则模板兜底
        List<Step> steps,
        List<String> sources) {

    /**
     * 单步执行轨迹：供前端「透明展示」AI 是如何一步步得出答案的。
     * status: done(已执行) / skipped(本次未涉及，已跳过)
     */
    public record Step(
            int index,
            String name,
            String status,
            long durationMs,
            String output) {
    }
}
