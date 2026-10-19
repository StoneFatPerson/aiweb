package com.aiweb.service;

import com.aiweb.entity.Question;
import com.aiweb.entity.WrongQuestion;
import com.aiweb.mapper.QuestionMapper;
import com.aiweb.mapper.WrongQuestionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            exist.setUserAnswer(trimTo(userAnswer, 255));
            exist.setLastRedoCorrect(null);
            wrongQuestionMapper.updateById(exist);
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
        wrongQuestionMapper.insert(w);
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
        LambdaQueryWrapper<WrongQuestion> query = new LambdaQueryWrapper<>();
        if (studentId != null && !studentId.isBlank()) {
            query.eq(WrongQuestion::getStudentId, studentId.trim());
        }
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
     * 重做判分：规则与学情诊断一致（选择题大小写不敏感；填空去空白转小写归一化）。
     * 判完更新重做次数与最近结果；**答对即从错题本移除**（错题的价值就是攻克它）。
     */
    public RedoResult gradeRedo(Long wrongId, String studentId, String answer) {
        WrongQuestion w = wrongQuestionMapper.selectById(wrongId);
        if (w == null || (studentId != null && !studentId.isBlank()
                && !studentId.trim().equals(w.getStudentId()))) {
            throw new IllegalArgumentException("错题不存在或不属于当前学生");
        }
        boolean correct = gradeAnswer(w.getCorrectAnswer(), answer);

        w.setRedoCount((w.getRedoCount() == null ? 0 : w.getRedoCount()) + 1);
        w.setLastRedoCorrect(correct ? 1 : 0);
        if (correct) {
            // 攻克成功：移出错题本（记录仍可用于统计的字段一并清理，直接删除即可）
            wrongQuestionMapper.deleteById(w.getId());
        } else {
            w.setUserAnswer(trimTo(answer, 255));
            wrongQuestionMapper.updateById(w);
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

    /** 用户手动移除一条错题（校验归属，防止删别人的） */
    public boolean remove(Long id, String studentId) {
        WrongQuestion w = wrongQuestionMapper.selectById(id);
        if (w == null) return false;
        if (studentId != null && !studentId.isBlank()
                && !studentId.trim().equals(w.getStudentId())) {
            return false;
        }
        return wrongQuestionMapper.deleteById(id) > 0;
    }

    /** 统计：总数 + 按科目分布 + 按来源分布 + 已重做过多少次 */
    public Map<String, Object> stats(String studentId) {
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

    private static String normalizeSubject(String subject) {
        if (subject == null || subject.isBlank()) return "math";
        String s = subject.trim().toLowerCase();
        return switch (s) {
            case "math", "english", "politics" -> s;
            default -> "math";
        };
    }

    private static String trimTo(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ===================== 传输记录 =====================

    /** 重做题目（刻意不带正确答案） */
    public record RedoItem(Long id, String subject, String source,
                           String stem, String type, String options, String note) {
    }

    /** 重做判分结果 */
    public record RedoResult(Long id, boolean correct, String correctAnswer, String normalizedAnswer) {
    }
}
