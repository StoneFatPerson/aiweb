package com.aiweb.dto;

import java.util.List;

/**
 * 提交测评答案的请求。
 * 位置：src/main/java/com/aiweb/dto/AssessmentSubmitRequest.java
 */
public record AssessmentSubmitRequest(String studentId, List<AnswerSubmission> answers) {

    /** 单条作答：题目编号 + 用户答案 */
    public record AnswerSubmission(String questionCode, String answer) {
    }
}
