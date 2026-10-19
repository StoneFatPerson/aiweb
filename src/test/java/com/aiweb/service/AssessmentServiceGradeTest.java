package com.aiweb.service;

import com.aiweb.entity.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 判分与答案归一化的单元测试。
 * 位置：src/test/java/com/aiweb/service/AssessmentServiceGradeTest.java
 *
 * 说明：grade / norm 已提为包级可见（为可测性的小重构），这里同包直接调。
 * 用 Mockito 造一个「依赖全是假」的 AssessmentService 实例——
 * 判分逻辑不触碰任何依赖字段，不启动 Spring、不连数据库。
 */
class AssessmentServiceGradeTest {

    private AssessmentService service;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(Mockito.mock(ChatClient.class));
        service = new AssessmentService(
                mock(com.aiweb.mapper.QuestionMapper.class),
                mock(com.aiweb.mapper.KnowledgePointMapper.class),
                mock(com.aiweb.mapper.MasteryMapper.class),
                mock(com.aiweb.mapper.AnswerHistoryMapper.class),
                mock(com.aiweb.mapper.WrongQuestionMapper.class),
                mock(DiagnosisService.class),
                mock(WrongQuestionService.class),
                mock(com.fasterxml.jackson.databind.ObjectMapper.class),
                builder,
                mock(OcrService.class));
    }

    private Question choice(String answer) {
        Question q = new Question();
        q.setCode("T-1");
        q.setType("SINGLE_CHOICE");
        q.setAnswer(answer);
        return q;
    }

    private Question fill(String answer) {
        Question q = new Question();
        q.setCode("T-2");
        q.setType("FILL_BLANK");
        q.setAnswer(answer);
        return q;
    }

    @Test
    @DisplayName("选择题：大小写不敏感")
    void choiceIsCaseInsensitive() {
        assertTrue(service.grade(choice("B"), "b"));
        assertTrue(service.grade(choice("B"), "B"));
        assertTrue(service.grade(choice("B"), " b "));
        assertFalse(service.grade(choice("B"), "C"));
    }

    @Test
    @DisplayName("填空题：去空白（含全角空格）、大小写不敏感")
    void fillBlankNormalization() {
        assertTrue(service.grade(fill("1/2"), " 1/2 "));
        assertTrue(service.grade(fill("1/2"), "1/2"));
        assertTrue(service.grade(fill("2x-1"), " 2X-1 "));
        assertTrue(service.grade(fill("abc"), "ABC"));
        assertFalse(service.grade(fill("1/2"), "2/1"));
    }

    @Test
    @DisplayName("空答案 / null 答案一律判错，不抛异常")
    void nullAndEmptyAreWrong() {
        assertFalse(service.grade(choice("B"), null));
        assertFalse(service.grade(choice("B"), ""));
        assertFalse(service.grade(choice("B"), "   "));
        assertFalse(service.grade(fill("1/2"), null));
    }

    @Test
    @DisplayName("归一化：全角空格、各种空白都去掉")
    void normStripsAllWhitespace() {
        assertEquals("1/2", service.norm(" 1 / 2 "));
        assertEquals("ab", service.norm("a　b")); // 全角空格
        assertEquals("", service.norm(null));
    }
}
