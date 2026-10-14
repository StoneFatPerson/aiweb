package com.aiweb.dto;

import java.util.List;

/**
 * 提交测评答案的请求。
 * 位置：src/main/java/com/aiweb/dto/AssessmentSubmitRequest.java
 */
public record AssessmentSubmitRequest(String studentId, List<AnswerSubmission> answers) {

    /**
     * 单条作答：题目编号 + 用户答案。
     * 两种提交方式「并行共存、互不替代」：
     *   - answer：文字输入（原有方式，适用于能直接打字的情况）
     *   - imageBase64 / imageMime：上传答案图片（新增方式，适用于含大量特殊符号的数学题）
     * 二者二选一；若带图片则以图片识别结果为准。
     */
    public record AnswerSubmission(String questionCode, String answer, String imageBase64, String imageMime) {
    }
}
