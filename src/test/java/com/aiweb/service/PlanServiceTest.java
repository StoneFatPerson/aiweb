package com.aiweb.service;

import com.aiweb.dto.AssessmentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 备考计划生成器的单元测试。
 * 位置：src/test/java/com/aiweb/service/PlanServiceTest.java
 *
 * 测什么：三阶段结构、薄弱点优先、空输入不崩——规则计划是全链路兜底，
 * 它必须任何情况下都返回结构完整的结果。
 */
class PlanServiceTest {

    private final PlanService service = new PlanService();

    private AssessmentResult.PointDiagnosis point(String name, double mastery) {
        return new AssessmentResult.PointDiagnosis("P-" + name, name, mastery,
                mastery < 0.4 ? "薄弱" : mastery < 0.7 ? "中等" : "扎实");
    }

    @Test
    @DisplayName("三阶段齐全且顺序正确")
    void threePhasesInOrder() {
        AssessmentResult.StudyPlan plan = service.buildPlan(List.of(point("极限", 0.2)));
        assertEquals(3, plan.phases().size());
        assertEquals("基础阶段", plan.phases().get(0).name());
        assertEquals("强化阶段", plan.phases().get(1).name());
        assertEquals("冲刺阶段", plan.phases().get(2).name());
        assertNotNull(plan.summary());
        assertFalse(plan.summary().isBlank());
    }

    @Test
    @DisplayName("薄弱知识点进入强化阶段重点，且汇总里点名")
    void weakPointsGoToReinforcePhase() {
        AssessmentResult.StudyPlan plan = service.buildPlan(List.of(
                point("极限", 0.2),
                point("微分", 0.35),
                point("积分", 0.9)));

        var reinforce = plan.phases().get(1);
        assertTrue(reinforce.points().contains("极限"), "强化阶段应包含薄弱点");
        assertTrue(reinforce.points().contains("微分"));
        assertFalse(reinforce.points().contains("积分"), "已掌握的不该占强化阶段");
        assertTrue(plan.summary().contains("极限"), "汇总应点名薄弱点");
        assertTrue(plan.summary().contains("2 个薄弱"));
    }

    @Test
    @DisplayName("没有薄弱点时走「无明显薄弱」分支，不崩")
    void noWeakPointsBranch() {
        AssessmentResult.StudyPlan plan = service.buildPlan(List.of(
                point("极限", 0.8),
                point("微分", 0.95)));
        var reinforce = plan.phases().get(1);
        assertTrue(reinforce.focus().contains("无明显薄弱"));
        assertTrue(plan.summary().contains("整体掌握良好"));
    }

    @Test
    @DisplayName("空诊断不崩：三阶段仍在，汇总非空")
    void emptyDiagnosisIsSafe() {
        AssessmentResult.StudyPlan plan = service.buildPlan(List.of());
        assertEquals(3, plan.phases().size());
        assertFalse(plan.summary().isBlank());
        assertTrue(plan.phases().get(0).points().isEmpty());
    }

    @Test
    @DisplayName("基础与冲刺阶段覆盖全部知识点（打底子 + 查漏补缺）")
    void baseAndSprintCoverAll() {
        var plan = service.buildPlan(List.of(point("A", 0.2), point("B", 0.5), point("C", 0.9)));
        assertEquals(3, plan.phases().get(0).points().size());
        assertEquals(3, plan.phases().get(2).points().size());
    }
}
