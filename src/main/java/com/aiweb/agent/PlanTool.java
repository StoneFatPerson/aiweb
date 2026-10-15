package com.aiweb.agent;

import com.aiweb.dto.AssessmentResult;
import org.springframework.stereotype.Component;

/**
 * 步骤5：生成备考计划。把诊断得到的三阶段计划整理成简明摘要。
 * 位置：src/main/java/com/aiweb/agent/PlanTool.java
 * 计划由 PlanService 规则生成（不经过大模型），稳定可演示。
 */
@Component
public class PlanTool implements AgentTool {

    @Override
    public String name() {
        return "生成备考计划";
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public boolean shouldRun(AgentContext ctx) {
        return ctx.hasAssessedDiagnosis();
    }

    @Override
    public ToolOutput execute(AgentContext ctx) {
        AssessmentResult.StudyPlan plan = ctx.getDiagnosis().plan();
        StringBuilder sb = new StringBuilder(plan.summary());
        for (AssessmentResult.PhasePlan p : plan.phases()) {
            sb.append("\n- ").append(p.name()).append("（").append(p.weeks()).append("）：").append(p.focus());
        }
        ctx.setPlanSummary(sb.toString());
        return ToolOutput.of(sb.toString());
    }
}
