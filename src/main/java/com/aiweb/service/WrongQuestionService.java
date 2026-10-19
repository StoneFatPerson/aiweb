package com.aiweb.service;

import com.aiweb.entity.Question;
import com.aiweb.entity.WrongQuestion;
import com.aiweb.mapper.QuestionMapper;
import com.aiweb.mapper.WrongQuestionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 错题本服务。
 * 位置：src/main/java/com/aiweb/service/WrongQuestionService.java
 *
 * 功能：
 *   1. recordSystemWrong  —— 学情诊断判错时自动记录（幂等：同一题重复答错只更新，不堆重复记录）；
 *   2. importManual       —— 用户导入自己的错题（题干/答案自带，不依赖题库）；
 *   3. list               —— 错题列表（可按科目筛选）；
 *   4. randomRedo         —— 随机抽题进入「重做模式」（SYSTEM 题回查题库拿完整题干/选项，MANUAL 题用自带内容）；
 *   5. gradeRedo          —— 重做判分（与诊断判分同一套规则：选择题大小写不敏感、填空归一化）；
 *   6. remove / stats     —— 移除与统计。
 */
@Service
public class WrongQuestionService {

    /** 重做模式默认抽题数 */
    public static final int DEFAULT_REDO_COUNT = 5;

    /** 单次随机抽题上限（防止误传巨大 count） */
    private static final int MAX_REDO_COUNT = 20;

    private final WrongQuestionMapper wrongQuestionMapper;
    private final QuestionMapper questionMapper;

    public WrongQuestionService(WrongQuestionMapper wrongQuestionMapper, QuestionMapper questionMapper) {
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.questionMapper = questionMapper;
    }

    // ===================== 1. 诊断判错自动记录 =====================

    /**
     * 学情诊断判错时调用。幂等策略：同一学生 + 同一题库题号只保留一条 SYSTEM 记录，
     * 重复答错时更新「当时的错误答案」并清空上次重做结果（重新进入待攻克状态）。
     */
    public void recordSystemWrong(String studentId, Question q, String userAnswer) {
        if (studentId == null || studentId.isBlank() || q == null) return;

        WrongQuestion exist = wrongQuestionMapper.selectOne(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId)
                .eq(WrongQuestion::getQuestionCode, q.getCode())
                .eq(WrongQuestion::getSource, "SYSTEM")
                .last("LIMIT 1"));
        if (exist != null) {
            // 注意：必须用 UpdateWrapper 显式 set null——MyBatis-Plus 的 updateById 默认
            // 跳过 null 字段（FieldStrategy.NOT_NULL），setLastRedoCorrect(null) 不会生成 SET 子句，
            // 「清空上次重做结果」会变成空操作（曾导致前端「最近重做：答错」永远擦不掉）。
            wrongQuestionMapper.update(null, new LambdaUpdateWrapper<WrongQuestion>()
                    .eq(WrongQuestion::getId, exist.getId())
                    .set(WrongQuestion::getUserAnswer, trimTo(userAnswer, 255))
                    .set(WrongQuestion::getLastRedoCorrect, null));
            return;
        }

        WrongQuestion w = new WrongQuestion();
        w.setStudentId(studentId);
        w.setSubject(q.getSubjectCode() == null ? "math" : q.getSubjectCode());
        w.setSource("SYSTEM");
        w.setQuestionCode(q.getCode());
        w.setStem(trimTo(q.getStem(), 2000));
        w.setUserAnswer(trimTo(userAnswer, 255));
        w.setCorrectAnswer(trimTo(q.getAnswer(), 255));
        w.setRedoCount(0);
        try {
            wrongQuestionMapper.insert(w);
        } catch (DuplicateKeyException e) {
            // 并发双提交（两个线程同时 selectOne 返回 null 再 insert）撞唯一键：
            // 回退为更新路径，保证幂等（唯一键 uk_student_qcode_src 兜底）
            wrongQuestionMapper.update(null, new LambdaUpdateWrapper<WrongQuestion>()
                    .eq(WrongQuestion::getStudentId, studentId)
                    .eq(WrongQuestion::getQuestionCode, q.getCode())
                    .eq(WrongQuestion::getSource, "SYSTEM")
                    .set(WrongQuestion::getUserAnswer, trimTo(userAnswer, 255))
                    .set(WrongQuestion::getLastRedoCorrect, null));
        }
    }

    // ===================== 2. 用户导入 =====================

    /** 用户导入错题：题干必填，答案与备注选填（导入时也可以还不知道正确答案） */
    public WrongQuestion importManual(String studentId, String subject, String stem,
                                      String userAnswer, String correctAnswer, String note) {
        if (studentId == null || studentId.isBlank()
                || stem == null || stem.isBlank()) {
            throw new IllegalArgumentException("学生标识与题干不能为空");
        }
        WrongQuestion w = new WrongQuestion();
        w.setStudentId(studentId.trim());
        w.setSubject(normalizeSubject(subject));
        w.setSource("MANUAL");
        w.setStem(trimTo(stem.trim(), 2000));
        w.setUserAnswer(trimTo(userAnswer, 255));
        w.setCorrectAnswer(trimTo(correctAnswer, 255));
        w.setNote(trimTo(note, 500));
        w.setRedoCount(0);
        wrongQuestionMapper.insert(w);
        return w;
    }

    // ===================== 3. 列表 =====================

    public List<WrongQuestion> list(String studentId, String subject) {
        requireStudentId(studentId);
        LambdaQueryWrapper<WrongQuestion> query = new LambdaQueryWrapper<>();
        query.eq(WrongQuestion::getStudentId, studentId.trim());
        if (subject != null && !subject.isBlank()) {
            query.eq(WrongQuestion::getSubject, normalizeSubject(subject));
        }
        query.orderByDesc(WrongQuestion::getCreatedAt);
        return wrongQuestionMapper.selectList(query);
    }

    // ===================== 4. 随机抽题（重做模式） =====================

    /**
     * 随机抽题。返回的条目不带正确答案（前端先作答，提交后由 gradeRedo 判分回传）。
     * SYSTEM 题回查题库拿完整题干与选项；MANUAL 题用导入时自带的内容。
     */
    public List<RedoItem> randomRedo(String studentId, String subject, int count) {
        int n = count <= 0 ? DEFAULT_REDO_COUNT : Math.min(count, MAX_REDO_COUNT);
        List<WrongQuestion> candidates = list(studentId, subject);
        if (candidates.isEmpty()) return List.of();

        Collections.shuffle(candidates); // 随机重做
        List<WrongQuestion> picked = candidates.size() > n ? candidates.subList(0, n) : candidates;

        // SYSTEM 题按 question_code 批量回查题库（拿 options / type）
        List<String> codes = picked.stream()
                .filter(w -> "SYSTEM".equals(w.getSource()) && w.getQuestionCode() != null)
                .map(WrongQuestion::getQuestionCode).toList();
        Map<String, Question> bank = new HashMap<>();
        if (!codes.isEmpty()) {
            for (Question q : questionMapper.selectList(new LambdaQueryWrapper<Question>()
                    .in(Question::getCode, codes))) {
                bank.put(q.getCode(), q);
            }
        }

        List<RedoItem> items = new ArrayList<>();
        for (WrongQuestion w : picked) {
            Question q = bank.get(w.getQuestionCode());
            String type = q != null ? q.getType() : (w.getCorrectAnswer() != null && w.getCorrectAnswer().length() == 1
                    ? "SINGLE_CHOICE" : "FILL_BLANK");
            items.add(new RedoItem(
                    w.getId(),
                    w.getSubject(),
                    w.getSource(),
                    q != null ? q.getStem() : w.getStem(),
                    type,
                    q != null ? q.getOptions() : null,
                    w.getNote()));
        }
        return items;
    }

    // ===================== 5. 重做判分 =====================

    /**
     * 批量重做判分（一次提交一整轮，交互与学情诊断的「提交并诊断」一致）。
     * 逐题独立判分：某题异常不影响其他题（与单题 gradeRedo 同一套规则）。
     */
    public List<RedoResult> gradeBatch(String studentId, List<RedoSubmission> submissions) {
        List<RedoResult> results = new ArrayList<>();
        if (submissions == null) return results;
        for (RedoSubmission s : submissions) {
            try {
                results.add(gradeRedo(s.wrongId(), studentId, s.answer()));
            } catch (Exception e) {
                // 单题异常（如错题已被移除）：记一条未判分结果，不中断整轮
                results.add(new RedoResult(s.wrongId(), false, null, null));
            }
        }
        return results;
    }

    /**
     * 重做判分：规则与学情诊断一致（选择题大小写不敏感；填空去空白转小写归一化）。
     * 判完更新重做次数与最近结果；**答对即从错题本移除**（错题的价值就是攻克它）。
     *
     * 安全：studentId 必填且必须匹配（fail-closed）——不允许「不传学号就操作任意错题」。
     * 计数：redo_count 用 SQL 原子自增，避免并发判分丢失更新。
     */
    public RedoResult gradeRedo(Long wrongId, String studentId, String answer) {
        requireStudentId(studentId);
        if (wrongId == null) {
            throw new IllegalArgumentException("缺少错题 ID");
        }
        WrongQuestion w = wrongQuestionMapper.selectById(wrongId);
        if (w == null || !studentId.trim().equals(w.getStudentId())) {
            throw new IllegalArgumentException("错题不存在或不属于当前学生");
        }
        boolean correct = gradeAnswer(w.getCorrectAnswer(), answer);

        // 原子自增重做次数 + 记录最近结果与本次作答
        wrongQuestionMapper.update(null, new LambdaUpdateWrapper<WrongQuestion>()
                .eq(WrongQuestion::getId, w.getId())
                .setSql("redo_count = redo_count + 1")
                .set(WrongQuestion::getLastRedoCorrect, correct ? 1 : 0)
                .set(WrongQuestion::getUserAnswer, trimTo(answer, 255)));
        if (correct) {
            // 攻克成功：条件删除（带学生归属），防止删掉并发期间易主的记录
            wrongQuestionMapper.delete(new LambdaQueryWrapper<WrongQuestion>()
                    .eq(WrongQuestion::getId, w.getId())
                    .eq(WrongQuestion::getStudentId, studentId.trim()));
        }
        return new RedoResult(w.getId(), correct, w.getCorrectAnswer(), norm(answer));
    }

    /** 判分：空答案判错；答案长度 1 视为选择题（大小写不敏感），否则按填空归一化比对 */
    static boolean gradeAnswer(String correctAnswer, String userAnswer) {
        if (correctAnswer == null || correctAnswer.isBlank()) return false;
        if (userAnswer == null || userAnswer.isBlank()) return false;
        String c = correctAnswer.trim();
        String u = userAnswer.trim();
        if (c.length() == 1) {
            return c.equalsIgnoreCase(u);
        }
        return norm(c).equals(norm(u));
    }

    /** 归一化：去所有空白（含全角空格）、转小写——与 AssessmentService 的判分规则保持一致 */
    static String norm(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", "").toLowerCase();
    }

    // ===================== 6. 移除与统计 =====================

    /** 学生标识必填（fail-closed）：本项目无登录体系，学号是错题本唯一的访问凭据，不允许空号操作 */
    private static void requireStudentId(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("缺少学生标识（studentId）");
        }
    }

    /** 用户手动移除一条错题（条件删除 + 校验归属，防止删别人的） */
    public boolean remove(Long id, String studentId) {
        requireStudentId(studentId);
        if (id == null) {
            throw new IllegalArgumentException("缺少错题 ID");
        }
        return wrongQuestionMapper.delete(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getId, id)
                .eq(WrongQuestion::getStudentId, studentId.trim())) > 0;
    }

    /** 统计：总数 + 按科目分布 + 按来源分布 + 已重做过多少次 */
    public Map<String, Object> stats(String studentId) {
        requireStudentId(studentId);
        List<WrongQuestion> all = list(studentId, null);
        Map<String, Long> bySubject = new HashMap<>();
        Map<String, Long> bySource = new HashMap<>();
        int redone = 0;
        for (WrongQuestion w : all) {
            bySubject.merge(w.getSubject() == null ? "unknown" : w.getSubject(), 1L, Long::sum);
            bySource.merge(w.getSource() == null ? "unknown" : w.getSource(), 1L, Long::sum);
            if (w.getRedoCount() != null && w.getRedoCount() > 0) redone++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", all.size());
        result.put("bySubject", bySubject);
        result.put("bySource", bySource);
        result.put("redone", redone);
        return result;
    }

    // ===================== 内部工具 =====================

    /** 科目归一化：只认 math/english/politics，其他一律报错（不再静默归到 math，学生会困惑） */
    private static String normalizeSubject(String subject) {
        if (subject == null || subject.isBlank()) return "math";
        String s = subject.trim().toLowerCase();
        if (Set.of("math", "english", "politics").contains(s)) return s;
        throw new IllegalArgumentException("不支持的科目：" + subject + "（仅 math / english / politics）");
    }

    private static String trimTo(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ===================== 传输记录 =====================

    /** 批量重做的一条提交 */
    public record RedoSubmission(Long wrongId, String answer) {
    }

    /** 重做题目（刻意不带正确答案） */
    public record RedoItem(Long id, String subject, String source,
                           String stem, String type, String options, String note) {
    }

    /** 重做判分结果 */
    public record RedoResult(Long id, boolean correct, String correctAnswer, String normalizedAnswer) {
    }
}
