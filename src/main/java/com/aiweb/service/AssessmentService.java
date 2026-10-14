package com.aiweb.service;

import com.aiweb.dto.AssessmentResult;
import com.aiweb.dto.AssessmentStartRequest;
import com.aiweb.dto.AssessmentStartResponse;
import com.aiweb.dto.AssessmentSubmitRequest;
import com.aiweb.entity.KnowledgePoint;
import com.aiweb.entity.Mastery;
import com.aiweb.entity.Question;
import com.aiweb.mapper.KnowledgePointMapper;
import com.aiweb.mapper.MasteryMapper;
import com.aiweb.mapper.QuestionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 测评业务编排：把「抽题 → 判分 → BKT 诊断 → 生成计划」串成一条闭环。
 * 位置：src/main/java/com/aiweb/service/AssessmentService.java
 *
 * 这是「学情诊断 + 题库接入」的大脑：
 *   start   —— 按学情抽题（不含答案）
 *   submit  —— 收答案、判分、更新各知识点掌握度、出诊断与计划
 */
@Service
public class AssessmentService {

    private final QuestionMapper questionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final MasteryMapper masteryMapper;
    private final DiagnosisService diagnosisService;
    private final PlanService planService;
    private final ObjectMapper objectMapper;

    public AssessmentService(QuestionMapper questionMapper,
                             KnowledgePointMapper knowledgePointMapper,
                             MasteryMapper masteryMapper,
                             DiagnosisService diagnosisService,
                             PlanService planService,
                             ObjectMapper objectMapper) {
        this.questionMapper = questionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.masteryMapper = masteryMapper;
        this.diagnosisService = diagnosisService;
        this.planService = planService;
        this.objectMapper = objectMapper;
    }

    /** 开始测评：抽取题目返回（不含答案） */
    public AssessmentStartResponse start(String studentId, String targetMath, int count) {
        List<Question> questions = questionMapper.selectList(null);

        // 按目标数学类型筛选（所有种子题都适用于 math1/2/3，这里保留逻辑以备扩充）
        if (targetMath != null && !targetMath.isBlank() && !"all".equalsIgnoreCase(targetMath)) {
            String t = targetMath.toLowerCase();
            questions = questions.stream()
                    .filter(q -> q.getApplicableTo() != null && q.getApplicableTo().toLowerCase().contains(t))
                    .collect(Collectors.toList());
        }

        int n = Math.min(count <= 0 ? questions.size() : count, questions.size());
        List<Question> picked = new ArrayList<>(questions);
        Collections.shuffle(picked);
        picked = picked.subList(0, n);

        List<AssessmentStartResponse.QuestionView> views =
                picked.stream().map(this::toView).collect(Collectors.toList());

        int totalPoints = Math.toIntExact(knowledgePointMapper.selectCount(null));
        return new AssessmentStartResponse(UUID.randomUUID().toString(), picked.size(), totalPoints, views);
    }

    /** 提交答案：判分 + 更新掌握度 + 出诊断与计划 */
    public AssessmentResult submit(String studentId, List<AssessmentSubmitRequest.AnswerSubmission> answers) {
        List<AssessmentResult.QuestionFeedback> feedback = new ArrayList<>();
        int correct = 0;

        for (AssessmentSubmitRequest.AnswerSubmission a : answers) {
            Question q = questionMapper.selectOne(
                    new LambdaQueryWrapper<Question>().eq(Question::getCode, a.questionCode()));
            if (q == null) continue;

            boolean ok = grade(q, a.answer());
            if (ok) correct++;

            // 该题涉及的知识点都要更新掌握度
            for (String pc : splitCodes(q.getPointCodes())) {
                updateMastery(studentId, pc, ok);
            }

            feedback.add(new AssessmentResult.QuestionFeedback(
                    q.getCode(), ok, norm(a.answer()),
                    q.getAnswer(), q.getExplanation(), splitCodes(q.getPointCodes())));
        }

        int total = answers.size();
        int score = total == 0 ? 0 : (int) Math.round(100.0 * correct / total);

        List<AssessmentResult.PointDiagnosis> diagnosis = buildDiagnosis(studentId);
        List<String> weak = diagnosis.stream()
                .filter(d -> d.mastery() < 0.4)
                .map(AssessmentResult.PointDiagnosis::pointName)
                .collect(Collectors.toList());
        AssessmentResult.StudyPlan plan = planService.buildPlan(diagnosis);

        return new AssessmentResult(total, correct, score, feedback, diagnosis, weak, plan);
    }

    /** 仅查看某学生的诊断与计划（不重新答题） */
    public AssessmentResult.DiagnosisView getDiagnosis(String studentId) {
        List<AssessmentResult.PointDiagnosis> diagnosis = buildDiagnosis(studentId);
        List<String> weak = diagnosis.stream()
                .filter(d -> d.mastery() < 0.4)
                .map(AssessmentResult.PointDiagnosis::pointName)
                .collect(Collectors.toList());
        // 是否真正做过测评：掌握度表里该学生有记录才算「已测评」，否则只是先验值
        long assessedCount = masteryMapper.selectCount(new LambdaQueryWrapper<Mastery>()
                .eq(Mastery::getStudentId, studentId));
        boolean assessed = assessedCount > 0;
        return new AssessmentResult.DiagnosisView(diagnosis, weak, planService.buildPlan(diagnosis), assessed);
    }

    public List<KnowledgePoint> allPoints() {
        return knowledgePointMapper.selectList(null);
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("questionCount", questionMapper.selectCount(null));
        m.put("pointCount", knowledgePointMapper.selectCount(null));
        return m;
    }

    // ===================== 以下为内部工具方法 =====================

    /** 把数据库里的题转成「无答案」的视图对象 */
    private AssessmentStartResponse.QuestionView toView(Question q) {
        List<String> pointCodes = splitCodes(q.getPointCodes());
        List<AssessmentStartResponse.QuestionView.OptionView> opts = null;
        if ("SINGLE_CHOICE".equals(q.getType()) && q.getOptions() != null) {
            try {
                opts = objectMapper.readValue(q.getOptions(),
                        objectMapper.getTypeFactory().constructCollectionType(
                                List.class, AssessmentStartResponse.QuestionView.OptionView.class));
            } catch (Exception e) {
                opts = List.of();
            }
        }
        return new AssessmentStartResponse.QuestionView(
                q.getCode(), q.getType(), q.getDifficulty(), q.getStem(), opts, pointCodes);
    }

    /** 判分：选择题大小写不敏感，填空题做归一化（去空格、转小写） */
    private boolean grade(Question q, String userAnswer) {
        if (userAnswer == null) return false;
        String u = norm(userAnswer);
        String a = norm(q.getAnswer());
        if ("FILL_BLANK".equals(q.getType())) {
            return u.equals(a);
        }
        return u.equalsIgnoreCase(a);
    }

    /** 答案归一化：去空白、转小写，避免「 c 」与「C」被判错 */
    private String norm(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("[\\s　]", "").toLowerCase();
    }

    /** 用 BKT 更新某学生在某知识点上的掌握度 */
    private void updateMastery(String studentId, String pointCode, boolean correct) {
        Mastery existing = masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>()
                .eq(Mastery::getStudentId, studentId)
                .eq(Mastery::getPointCode, pointCode));

        double oldProb = existing != null && existing.getMasteryProb() != null
                ? existing.getMasteryProb().doubleValue()
                : diagnosisService.getPrior();
        double newProb = diagnosisService.update(oldProb, correct);

        if (existing == null) {
            Mastery m = new Mastery();
            m.setStudentId(studentId);
            m.setPointCode(pointCode);
            m.setMasteryProb(BigDecimal.valueOf(newProb));
            m.setAttempts(1);
            masteryMapper.insert(m);
        } else {
            int attempts = (existing.getAttempts() == null ? 0 : existing.getAttempts()) + 1;
            masteryMapper.update(null, new LambdaUpdateWrapper<Mastery>()
                    .eq(Mastery::getStudentId, studentId)
                    .eq(Mastery::getPointCode, pointCode)
                    .set(Mastery::getMasteryProb, BigDecimal.valueOf(newProb))
                    .set(Mastery::getAttempts, attempts));
        }
    }

    /** 构造某学生的全量知识点诊断（未作答的知识点用先验 0.3） */
    private List<AssessmentResult.PointDiagnosis> buildDiagnosis(String studentId) {
        List<KnowledgePoint> points = knowledgePointMapper.selectList(null);
        List<AssessmentResult.PointDiagnosis> list = new ArrayList<>();
        for (KnowledgePoint kp : points) {
            Mastery m = (studentId != null) ? masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>()
                    .eq(Mastery::getStudentId, studentId)
                    .eq(Mastery::getPointCode, kp.getCode())) : null;
            double prob = (m != null && m.getMasteryProb() != null)
                    ? m.getMasteryProb().doubleValue()
                    : diagnosisService.getPrior();
            String level = prob < 0.4 ? "薄弱" : (prob < 0.7 ? "中等" : "扎实");
            list.add(new AssessmentResult.PointDiagnosis(
                    kp.getCode(), kp.getName(), Math.round(prob * 1000) / 1000.0, level));
        }
        // 薄弱的排前面，方便前端优先展示
        list.sort(Comparator.comparingDouble(AssessmentResult.PointDiagnosis::mastery));
        return list;
    }

    /** "MATH-01-01,MATH-01-02" -> ["MATH-01-01","MATH-01-02"] */
    private List<String> splitCodes(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .collect(Collectors.toList());
    }
}
