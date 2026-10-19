package com.aiweb.service;

import com.aiweb.entity.Question;
import com.aiweb.entity.WrongQuestion;
import com.aiweb.mapper.QuestionMapper;
import com.aiweb.mapper.WrongQuestionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 错题本服务的单元测试。
 * 位置：src/test/java/com/aiweb/service/WrongQuestionServiceTest.java
 *
 * 测什么：判分规则（与诊断一致）、幂等自动记录、随机重做判分后的
 * 「答对移除 / 答错更新」、导入校验、归属安全（不能删/改别人的错题）。
 *
 * 注意：MyBatis-Plus BaseMapper 的 insert/updateById 有单实体与 Collection 两个重载，
 * verify 时用 ArgumentCaptor 捕获参数（避免 lambda 匹配器的重载推断歧义）。
 */
class WrongQuestionServiceTest {

    private WrongQuestionMapper wrongMapper;
    private QuestionMapper questionMapper;
    private WrongQuestionService service;

    @BeforeEach
    void setUp() {
        // 无 Spring 环境下手动初始化 MP 的 lambda 缓存（否则 LambdaQueryWrapper.in() 报
        // "can not find lambda cache"；正常启动时由 Spring 自动完成）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Question.class);
        TableInfoHelper.initTableInfo(assistant, WrongQuestion.class);

        wrongMapper = Mockito.mock(WrongQuestionMapper.class);
        questionMapper = Mockito.mock(QuestionMapper.class);
        service = new WrongQuestionService(wrongMapper, questionMapper);
    }

    private Question bankQuestion(String code, String type, String answer) {
        Question q = new Question();
        q.setCode(code);
        q.setType(type);
        q.setAnswer(answer);
        q.setStem("题干：" + code);
        q.setOptions("[{\"key\":\"A\",\"content\":\"选项A\"}]");
        q.setSubjectCode("math");
        return q;
    }

    private WrongQuestion existing(Long id, String studentId, String code, String answer) {
        WrongQuestion w = new WrongQuestion();
        w.setId(id);
        w.setStudentId(studentId);
        w.setSubject("math");
        w.setSource("SYSTEM");
        w.setQuestionCode(code);
        w.setCorrectAnswer(answer);
        w.setRedoCount(0);
        return w;
    }

    /** 捕获 update(null, wrapper) 的 wrapper（BaseMapper 重载消歧） */
    private static com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WrongQuestion> captureUpdateWrapper(WrongQuestionMapper mapper) {
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WrongQuestion>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), captor.capture());
        return captor.getValue();
    }

    private static WrongQuestion captureInsert(WrongQuestionMapper mapper) {
        ArgumentCaptor<WrongQuestion> captor = ArgumentCaptor.forClass(WrongQuestion.class);
        verify(mapper).insert(captor.capture());
        return captor.getValue();
    }

    // ===================== 判分规则 =====================

    @Test
    @DisplayName("判分：选择题大小写不敏感，填空归一化（全角空格），null 安全")
    void gradeRulesMatchAssessment() {
        // 单字母答案按选择题：大小写不敏感
        assertTrue(WrongQuestionService.gradeAnswer("B", "b"));
        assertTrue(WrongQuestionService.gradeAnswer("B", " B "));
        assertFalse(WrongQuestionService.gradeAnswer("B", "C"));
        // 多字符答案按填空：去空白转小写
        assertTrue(WrongQuestionService.gradeAnswer("1/2", " 1 / 2 "));
        assertTrue(WrongQuestionService.gradeAnswer("2x-1", " 2X-1 "));
        assertFalse(WrongQuestionService.gradeAnswer("1/2", "2/1"));
        // null / 空一律判错，不抛异常
        assertFalse(WrongQuestionService.gradeAnswer("B", null));
        assertFalse(WrongQuestionService.gradeAnswer(null, "B"));
        assertFalse(WrongQuestionService.gradeAnswer("B", ""));
    }

    // ===================== 幂等自动记录 =====================

    @Test
    @DisplayName("自动记录：重复答错同一题只更新，不堆重复记录（且必须显式 set null 清空重做结果）")
    void recordSystemWrongIsIdempotent() {
        // 已有一条 SYSTEM 记录 → 走更新
        when(wrongMapper.selectOne(any())).thenReturn(existing(7L, "s1", "MATH-Q0001", "A"));
        service.recordSystemWrong("s1", bankQuestion("MATH-Q0001", "SINGLE_CHOICE", "A"), "C");

        // 验证走的是 UpdateWrapper（不是 updateById）——因为 MyBatis-Plus 的 updateById 会跳过
        // null 字段，setLastRedoCorrect(null) 不会生成 SET 子句，「清空上次重做结果」会失效。
        // 必须显式 .set(column, null) 让 SQL 里出现 last_redo_correct=NULL。
        var wrapper = captureUpdateWrapper(wrongMapper);
        String sqlSet = wrapper.getSqlSet().toLowerCase();
        assertTrue(sqlSet.contains("user_answer"), "应更新错误答案，实际 SQL SET：" + sqlSet);
        assertTrue(sqlSet.contains("last_redo_correct"), "应显式 set last_redo_correct 列，实际 SQL SET：" + sqlSet);
        assertTrue(wrapper.getParamNameValuePairs().containsValue(null),
                "last_redo_correct 的参数值应为 null（MP 的 set(column,null) 走参数占位符，值仍是 null）");
        verify(wrongMapper, never()).insert(any(WrongQuestion.class));
    }

    @Test
    @DisplayName("自动记录：首次答错插入新记录")
    void recordSystemWrongInsertsNew() {
        when(wrongMapper.selectOne(any())).thenReturn(null);
        service.recordSystemWrong("s1", bankQuestion("MATH-Q0002", "FILL_BLANK", "1/2"), "2/3");

        WrongQuestion saved = captureInsert(wrongMapper);
        assertEquals("s1", saved.getStudentId());
        assertEquals("SYSTEM", saved.getSource());
        assertEquals("MATH-Q0002", saved.getQuestionCode());
        assertEquals("2/3", saved.getUserAnswer());
        assertEquals("1/2", saved.getCorrectAnswer());
        assertNotNull(saved.getStem());
        assertTrue(saved.getStem().contains("题干"), "题干应冗余存储，避免题库变动后丢上下文");
    }

    // ===================== 随机重做判分 =====================

    @Test
    @DisplayName("重做答对：自动移出错题本（攻克即移除）")
    void gradeRedoCorrectRemoves() {
        when(wrongMapper.selectById(99L)).thenReturn(existing(99L, "s1", "MATH-Q0001", "B"));

        WrongQuestionService.RedoResult r = service.gradeRedo(99L, "s1", "b");

        assertTrue(r.correct());
        verify(wrongMapper).delete(any(LambdaQueryWrapper.class)); // 条件删除（id + student_id）
    }

    @Test
    @DisplayName("重做答错：留在本里，更新错误答案与重做次数")
    void gradeRedoWrongKeeps() {
        when(wrongMapper.selectById(99L)).thenReturn(existing(99L, "s1", "MATH-Q0001", "B"));

        WrongQuestionService.RedoResult r = service.gradeRedo(99L, "s1", "D");

        assertFalse(r.correct());
        var wrapper = captureUpdateWrapper(wrongMapper);
        String sqlSet = wrapper.getSqlSet().toLowerCase();
        assertTrue(sqlSet.contains("redo_count = redo_count + 1"),
                "redo_count 必须 SQL 原子自增，实际 SQL SET：" + sqlSet);
        verify(wrongMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("安全：不能判分/移除别人的错题")
    void gradeRedoRejectsOtherStudent() {
        when(wrongMapper.selectById(99L)).thenReturn(existing(99L, "someone-else", "MATH-Q0001", "B"));
        assertThrows(IllegalArgumentException.class, () -> service.gradeRedo(99L, "s1", "B"));

        // 移除同理：条件删除带 student_id 归属，动不了别人的
        service.remove(99L, "s1");
        ArgumentCaptor<LambdaQueryWrapper<WrongQuestion>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(wrongMapper).delete(cap.capture());
        assertTrue(cap.getValue().getTargetSql().toLowerCase().contains("student_id"),
                "删除必须带 student_id 条件");
    }

    @Test
    @DisplayName("安全：不传 studentId 一律拒绝（fail-closed，不允许空号操作任意错题）")
    void missingStudentIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.gradeRedo(99L, null, "B"));
        assertThrows(IllegalArgumentException.class, () -> service.gradeRedo(99L, "  ", "B"));
        assertThrows(IllegalArgumentException.class, () -> service.remove(99L, null));
        assertThrows(IllegalArgumentException.class, () -> service.list(null, null));
        assertThrows(IllegalArgumentException.class, () -> service.stats(null));
        verify(wrongMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    // ===================== 随机抽题 =====================

    @Test
    @DisplayName("随机抽题：SYSTEM 题回查题库拿完整题干与选项，且不泄露答案")
    void randomRedoBuildsItemsFromBank() {
        WrongQuestion w = existing(1L, "s1", "MATH-Q0001", "B");
        when(wrongMapper.selectList(any())).thenReturn(List.of(w));
        when(questionMapper.selectList(any())).thenReturn(
                List.of(bankQuestion("MATH-Q0001", "SINGLE_CHOICE", "B")));

        List<WrongQuestionService.RedoItem> items = service.randomRedo("s1", null, 5);

        assertEquals(1, items.size());
        WrongQuestionService.RedoItem item = items.get(0);
        assertEquals("题干：MATH-Q0001", item.stem(), "应使用题库完整题干");
        assertEquals("SINGLE_CHOICE", item.type());
        assertNotNull(item.options(), "选择题应带选项");
        // RedoItem 结构里根本没有 correctAnswer 字段——从结构上保证不泄露答案
    }

    @Test
    @DisplayName("随机抽题：空错题本返回空列表，不抛异常")
    void randomRedoEmptyIsSafe() {
        when(wrongMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.randomRedo("s1", null, 5).isEmpty());
    }

    // ===================== 导入 =====================

    @Test
    @DisplayName("导入：题干必填，非法科目归一化为 math")
    void importManualValidatesAndNormalizes() {
        assertThrows(IllegalArgumentException.class,
                () -> service.importManual("s1", "math", "  ", null, null, null));

        service.importManual("s1", "MATH", "我的错题题干", "A", "B", "粗心");
        WrongQuestion saved = captureInsert(wrongMapper);
        assertEquals("math", saved.getSubject(), "大写科目应归一化");
        assertEquals("MANUAL", saved.getSource());
        assertEquals("我的错题题干", saved.getStem());
    }
}
