package com.aiweb.agent;

import com.aiweb.dto.AssessmentResult;
import com.aiweb.service.AssessmentService;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 步骤4：学情诊断。用户提供了学号时，拉取其知识点掌握度（BKT 算法结果）。
 * 位置：src/main/java/com/aiweb/agent/DiagnosisTool.java
 */
@Component
public class DiagnosisTool implements AgentTool {

    private final AssessmentService assessmentService;

    public DiagnosisTool(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @Override
    public String name() {
        return "学情诊断";
    }

    @Override
    public int order() {
        return 4;
    }

    @Override
    public boolean shouldRun(AgentContext ctx) {
        return ctx.isRunDiagnosis();
    }

    @Override
    public ToolOutput execute(AgentContext ctx) {
        // Agent 学情诊断当前按数学科目（数学是考研诊断的主战场；英语/政治在测评页独立追踪）
        AssessmentResult.DiagnosisView view = assessmentService.getDiagnosis(ctx.getStudentId(), "math");
        ctx.setDiagnosis(view);

        if (!view.assessed()) {
            String note = "学号 " + ctx.getStudentId() + " 暂无测评记录，掌握度按初始值估计；"
                    + "可先到「学情诊断」页完成一次测评，再来生成个性化计划。";
            return ToolOutput.of(note);
        }

        String weak = view.weakPoints().isEmpty()
                ? "暂无显著薄弱点"
                : view.weakPoints().stream().limit(6).collect(Collectors.joining("、"));
        String summary = "已完成测评，覆盖 " + view.diagnosis().size() + " 个知识点；薄弱项：" + weak + "。";
        return ToolOutput.of(summary);
    }
}
