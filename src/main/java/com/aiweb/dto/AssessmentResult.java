package com.aiweb.dto;

import java.util.List;

/**
 * 测评结果：分数 + 逐题反馈 + 知识点诊断 + 薄弱点。
 * 位置：src/main/java/com/aiweb/dto/AssessmentResult.java
 *
 * 这是「学情诊断 + 题库接入」闭环的最终产出，前端用它一次性展示：
 *   1) 本次得分  2) 每题对错与解析  3) 各知识点掌握度  4) 薄弱点
 * （判错的题会自动进入错题本，见 WrongQuestionService）
 */
public record AssessmentResult(
        int total,
        int correctCount,
        int score,
        List<QuestionFeedback> feedback,
        List<PointDiagnosis> diagnosis,
        List<String> weakPoints) {

    /**
     * 单题反馈（含题干、正确答案与解析，便于错题学习）。
     * stem 必须带上：前端逐题解析要原样再现题目要求，
     * 只给 code（如 ENGLISH-Q0023）用户根本不知道是哪道题。
     *
     * @param scoringMode 评分方式：AUTO=系统客观判分 / AI=大模型主观评分
     * @param aiScore     仅 AI 评分题有值，0-100 分；客观题恒为 null
     * @param aiComment   仅 AI 评分题有值，阅卷简评；客观题恒为 null
     */
    public record QuestionFeedback(String code, String stem, boolean correct, String yourAnswer,
                                   String correctAnswer, String explanation, List<String> pointCodes,
                                   String scoringMode, Integer aiScore, String aiComment) {
    }

    /** 单个知识点的诊断：掌握概率 + 等级 */
    public record PointDiagnosis(String pointCode, String pointName, double mastery, String level) {
    }

    /** 仅诊断视图（GET /api/assess/diagnosis 返回，不含逐题反馈） */
    public record DiagnosisView(List<PointDiagnosis> diagnosis, List<String> weakPoints, boolean assessed) {
    }

    /** 做题历史中的一条记录（含对应题目的标准答案） */
    public record HistoryItem(String questionCode, String stem, String yourAnswer,
                             String correctAnswer, boolean correct, String createdAt) {
    }
}
