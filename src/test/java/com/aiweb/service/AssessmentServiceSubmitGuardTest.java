package com.aiweb.service;

import com.aiweb.dto.AssessmentSubmitRequest;
import com.aiweb.entity.Question;
import com.aiweb.mapper.AnswerHistoryMapper;
import com.aiweb.mapper.KnowledgePointMapper;
import com.aiweb.mapper.MasteryMapper;
import com.aiweb.mapper.QuestionMapper;
import com.aiweb.mapper.WrongQuestionMapper;
import com.aiweb.service.OcrService.OcrResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 学情诊断提交防护逻辑的单元测试。
 * 位置：src/test/java/com/aiweb/service/AssessmentServiceSubmitGuardTest.java
 *
 * 测什么（全部是 docs/测试报告.md 记录的真实缺陷修复点）：
 *   1) answers 为 null/空/超量 → 明确拒绝（不再 NPE 500）
 *   2) 图片 base64 超限/格式错 → 明确拒绝（防内存放大）
 *   3) OCR 识别失败 ≠ 学生答错：不计分、不进错题本、不更新 BKT（曾把学生正确答案冤枉判错）
 *   4) 文字判错才进错题本（自动收录路径保持工作）
 */
class AssessmentServiceSubmitGuardTest {

    private AssessmentService service;
    private QuestionMapper questionMapper;
    private WrongQuestionService wrongQuestionService;
    private OcrService ocrService;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(Mockito.mock(ChatClient.class));
        questionMapper = mock(QuestionMapper.class);
        wrongQuestionService = mock(WrongQuestionService.class);
        ocrService = mock(OcrService.class);

        service = new AssessmentService(
                questionMapper,
                mock(KnowledgePointMapper.class),
                mock(MasteryMapper.class),
                mock(AnswerHistoryMapper.class),
                mock(WrongQuestionMapper.class),
                mock(DiagnosisService.class),
                wrongQuestionService,
                mock(com.fasterxml.jackson.databind.ObjectMapper.class),
                builder,
                ocrService);
    }

    private Question choiceQuestion() {
        Question q = new Question();
        q.setCode("MATH-Q0001");
        q.setType("SINGLE_CHOICE");
        q.setAnswer("B");
        q.setSubjectCode("math");
        q.setPointCodes("MATH-01-01");
        return q;
    }

    @Test
    @DisplayName("answers 为 null / 空 → IllegalArgumentException（不再 NPE 500）")
    void nullOrEmptyAnswersRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.submit("s1", null));
        assertThrows(IllegalArgumentException.class, () -> service.submit("s1", List.of()));
    }

    @Test
    @DisplayName("单次提交超过 50 题 → 拒绝（防批量放大攻击）")
    void tooManyAnswersRejected() {
        List<AssessmentSubmitRequest.AnswerSubmission> many = new java.util.ArrayList<>();
        for (int i = 0; i < 51; i++) {
            many.add(new AssessmentSubmitRequest.AnswerSubmission("MATH-Q" + i, "B", null, null));
        }
        assertThrows(IllegalArgumentException.class, () -> service.submit("s1", many));
    }

    @Test
    @DisplayName("图片 base64 超 8MB → 拒绝（防内存放大）")
    void oversizedImageRejected() {
        when(questionMapper.selectOne(any())).thenReturn(choiceQuestion());
        String huge = "A".repeat(8 * 1024 * 1024 + 10);
        assertThrows(IllegalArgumentException.class, () -> service.submit("s1",
                List.of(new AssessmentSubmitRequest.AnswerSubmission("MATH-Q0001", null, huge, "image/jpeg"))));
    }

    @Test
    @DisplayName("非法 base64 → 拒绝（不再整轮 500）")
    void malformedBase64Rejected() {
        when(questionMapper.selectOne(any())).thenReturn(choiceQuestion());
        assertThrows(IllegalArgumentException.class, () -> service.submit("s1",
                List.of(new AssessmentSubmitRequest.AnswerSubmission("MATH-Q0001", null, "!!!not-base64!!!", "image/jpeg"))));
    }

    @Test
    @DisplayName("核心承诺：OCR 识别失败不计为答错——不进错题本、不冤判")
    void ocrFailureIsNotCountedAsWrong() {
        when(questionMapper.selectOne(any())).thenReturn(choiceQuestion());
        // 模拟免费档限流：OCR 返回 error、answer 为空
        when(ocrService.recognize(any(), any(), any(), any()))
                .thenReturn(new OcrResult("", false, "OCR 调用失败：rate limited"));

        // 不抛异常即通过（曾因把识别失败判为答错而污染 BKT 与错题本）
        var result = service.submit("s1", List.of(
                new AssessmentSubmitRequest.AnswerSubmission("MATH-Q0001", null,
                        java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}), "image/jpeg")));

        assertEquals(0, result.correctCount(), "识别失败的题不计对");
        assertEquals(0, result.score(), "识别失败的题不参与计分");
        // 关键：不进错题本
        verify(wrongQuestionService, never()).recordSystemWrong(any(), any(), any());
    }

    @Test
    @DisplayName("文字判错：照常自动收录进错题本（正常路径不回归）")
    void textWrongAnswerStillRecorded() {
        when(questionMapper.selectOne(any())).thenReturn(choiceQuestion());

        service.submit("s1", List.of(
                new AssessmentSubmitRequest.AnswerSubmission("MATH-Q0001", "C", null, null)));

        verify(wrongQuestionService, times(1)).recordSystemWrong(eq("s1"), any(Question.class), anyString());
    }

    @Test
    @DisplayName("文字答对：不进错题本")
    void textCorrectAnswerNotRecorded() {
        when(questionMapper.selectOne(any())).thenReturn(choiceQuestion());

        var result = service.submit("s1", List.of(
                new AssessmentSubmitRequest.AnswerSubmission("MATH-Q0001", "B", null, null)));

        assertEquals(1, result.correctCount());
        verify(wrongQuestionService, never()).recordSystemWrong(any(), any(), any());
    }
}
