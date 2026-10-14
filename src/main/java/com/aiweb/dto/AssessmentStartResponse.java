package com.aiweb.dto;

import java.util.List;

/**
 * 开始测评的返回：一组题目（刻意不含答案，防止偷看）。
 * 位置：src/main/java/com/aiweb/dto/AssessmentStartResponse.java
 */
public record AssessmentStartResponse(
        String sessionId,
        int totalQuestions,
        int totalPoints,
        List<QuestionView> questions) {

    /** 返回给前端的单道题（去掉 answer / explanation） */
    public record QuestionView(
            String code,
            String type,
            int difficulty,
            String stem,
            List<OptionView> options,
            List<String> pointCodes) {

        /** 选择题选项（key 如 A/B/C/D，content 为选项文本） */
        public record OptionView(String key, String content) {
        }
    }
}
