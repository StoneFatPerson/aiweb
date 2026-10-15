package com.aiweb.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 自研 Agent 编排器（对应 README 的「编排层 Orchestrator」）。
 * 位置：src/main/java/com/aiweb/agent/AgentOrchestrator.java
 *
 * 流程：理解需求 → 按意图依次调用各工具（每步记录轨迹）→ 汇总生成。
 *
 * 设计原则（与全项目一致）：
 *   - 事实来自数据库 / 知识库，大模型只负责组织语言，从根上避免幻觉；
 *   - 模型（免费档）限流或故障时，用规则模板兜底，保证任何情况下都有用、不报错。
 */
@Service
public class AgentOrchestrator {

    private final List<AgentTool> tools;
    private final ChatClient chatClient;

    public AgentOrchestrator(List<AgentTool> tools, ChatClient.Builder chatClientBuilder) {
        // 按 order() 升序排列，保证 IntentTool 最先跑（它决定后续跑哪些工具）
        this.tools = tools.stream()
                .sorted(Comparator.comparingInt(AgentTool::order))
                .toList();
        this.chatClient = chatClientBuilder.build();
    }

    /** 运行一次 Agent 编排，返回汇总答案 + 每步轨迹 + 参考来源 */
    public AgentResult run(String goal, String studentId) {
        String g = (goal == null) ? "" : goal.trim();
        if (g.isEmpty()) {
            return new AgentResult("",
                    "请先描述你的目标，例如：「我想考山东大学的计算机专业，该怎么准备？」",
                    false, List.of(), List.of());
        }

        AgentContext ctx = new AgentContext(g, studentId);
        List<AgentResult.Step> steps = new ArrayList<>();
        int idx = 1;
        for (AgentTool tool : tools) {
            String name = tool.name();
            if (!tool.shouldRun(ctx)) {
                steps.add(new AgentResult.Step(idx++, name, "skipped", 0, "（本次未涉及，已跳过）"));
                continue;
            }
            long start = System.currentTimeMillis();
            String output;
            try {
                output = tool.execute(ctx).summary();
            } catch (Exception e) {
                output = "（该步骤执行出错：" + e.getMessage() + "）";
            }
            long dur = System.currentTimeMillis() - start;
            steps.add(new AgentResult.Step(idx++, name, "done", dur, output));
        }

        boolean[] degraded = {false};
        String answer = synthesize(ctx, degraded);
        List<String> sources = new ArrayList<>(new LinkedHashSet<>(ctx.getScienceSources()));
        return new AgentResult(ctx.getGoal(), answer, degraded[0], steps, sources);
    }

    /** 汇总：大模型把各工具产出组织成连贯建议；失败则用规则模板兜底 */
    private String synthesize(AgentContext ctx, boolean[] degraded) {
        StringBuilder material = new StringBuilder();
        if (!ctx.getCollegeNote().isBlank()) {
            material.append("【院校专业】\n").append(ctx.getCollegeNote()).append("\n\n");
        }
        if (!ctx.getScienceAnswer().isBlank()) {
            material.append("【科普知识】\n").append(ctx.getScienceAnswer()).append("\n\n");
        }
        if (ctx.hasAssessedDiagnosis()) {
            material.append("【学情诊断与计划】\n").append(ctx.getPlanSummary()).append("\n\n");
        } else if (ctx.getDiagnosis() != null) {
            material.append("【学情诊断】\n该学生尚未测评，掌握度为初始估计。\n\n");
        }
        if (material.length() == 0) {
            material.append("（暂无检索到相关资料）\n\n");
        }

        String system = "你是考研备考规划助手。请基于下方【资料】，针对用户目标给出一段连贯、易懂、可执行的建议。"
                + "严格要求：只使用资料中的事实，不编造任何数据、分数线或政策；涉及政策与招生信息时，提醒以研招网和目标院校官网为准；"
                + "语气鼓励、条理清晰，适合本科生阅读。";
        String user = "【用户目标】" + ctx.getGoal() + "\n\n【资料】\n" + material + "\n请据此给出针对性建议。";

        try {
            String content = chatClient.prompt().system(system).user(user).call().content();
            if (content != null && !content.isBlank()) {
                return content;
            }
            degraded[0] = true;
            return fallback(ctx);
        } catch (Exception e) {
            degraded[0] = true;
            return fallback(ctx);
        }
    }

    /** 大模型不可用时的规则模板兜底：把各工具产出直接组织成答案 */
    private String fallback(AgentContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("（大模型接口暂时不可用，以下为基于知识库与数据库的整理结果，稍后可重试获取智能润色。）\n\n");
        if (!ctx.getCollegeNote().isBlank()) {
            sb.append("■ 院校专业\n").append(ctx.getCollegeNote()).append("\n\n");
        }
        if (!ctx.getScienceAnswer().isBlank()) {
            sb.append("■ 科普知识\n").append(ctx.getScienceAnswer()).append("\n\n");
        }
        if (ctx.hasAssessedDiagnosis()) {
            sb.append("■ 学情诊断与计划\n").append(ctx.getPlanSummary()).append("\n\n");
        }
        sb.append("提示：考试政策、时间节点、招生信息请以中国研究生招生信息网和目标院校研究生院官网公告为准。");
        return sb.toString();
    }
}
