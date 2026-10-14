package com.aiweb.dto;

import java.util.List;

/**
 * 测评结果：分数 + 逐题反馈 + 知识点诊断 + 备考计划。
 * 位置：src/main/java/com/aiweb/dto/AssessmentResult.java
 *
 * 这是「学情诊断 + 题库接入」闭环的最终产出，前端用它一次性展示：
 *   1) 本次得分  2) 每题对错与解析  3) 各知识点掌握度  4) 薄弱点  5) 三阶段备考计划
 */
public record AssessmentResult(
        int total,
        int correctCount,
        int score,
        List<QuestionFeedback> feedback,
        List<PointDiagnosis> diagnosis,
        List<String> weakPoints,
        StudyPlan plan) {

    /** 单题反馈（含正确答案与解析，便于错题学习） */
    public record QuestionFeedback(String code, boolean correct, String yourAnswer,
                                   String correctAnswer, String explanation, List<String> pointCodes) {
    }

    /** 单个知识点的诊断：掌握概率 + 等级 */
    public record PointDiagnosis(String pointCode, String pointName, double mastery, String level) {
    }

    /** 备考计划（基础/强化/冲刺三阶段） */
    public record StudyPlan(List<PhasePlan> phases, String summary) {
    }

    /** 单个阶段 */
    public record PhasePlan(String name, String weeks, String focus, List<String> points) {
    }

    /** 仅诊断视图（GET /api/assess/diagnosis 返回，不含逐题反馈） */
    public record DiagnosisView(List<PointDiagnosis> diagnosis, List<String> weakPoints, StudyPlan plan, boolean assessed) {
    }
}
