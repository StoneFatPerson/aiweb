package com.aiweb.service;

import com.aiweb.dto.AssessmentResult;
import com.aiweb.dto.AssessmentStartRequest;
import com.aiweb.dto.AssessmentStartResponse;
import com.aiweb.dto.AssessmentSubmitRequest;
import com.aiweb.entity.AnswerHistory;
import com.aiweb.entity.KnowledgePoint;
import com.aiweb.entity.Mastery;
import com.aiweb.entity.Question;
import com.aiweb.mapper.AnswerHistoryMapper;
import com.aiweb.service.OcrService.OcrResult;
import com.aiweb.mapper.KnowledgePointMapper;
import com.aiweb.mapper.MasteryMapper;
import com.aiweb.mapper.QuestionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.Base64;
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
    private final AnswerHistoryMapper answerHistoryMapper;
    private final DiagnosisService diagnosisService;
    private final PlanService planService;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final OcrService ocrService;

    public AssessmentService(QuestionMapper questionMapper,
                             KnowledgePointMapper knowledgePointMapper,
                             MasteryMapper masteryMapper,
                             AnswerHistoryMapper answerHistoryMapper,
                             DiagnosisService diagnosisService,
                             PlanService planService,
                             ObjectMapper objectMapper,
                             ChatClient.Builder chatClientBuilder,
                             OcrService ocrService) {
        this.questionMapper = questionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.masteryMapper = masteryMapper;
        this.answerHistoryMapper = answerHistoryMapper;
        this.diagnosisService = diagnosisService;
        this.planService = planService;
        this.objectMapper = objectMapper;
        this.chatClient = chatClientBuilder.build();
        this.ocrService = ocrService;
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

            // 两种提交方式并存：带图片走 OCR，否则走文字判分
            boolean ok;
            String finalUserAnswer;
            boolean fromImage = a.imageBase64() != null && !a.imageBase64().isBlank();
            if (fromImage) {
                OcrResult ocr = ocrService.recognize(
                        Base64.getDecoder().decode(a.imageBase64()),
                        a.imageMime(),
                        q.getAnswer(),
                        q.getType());
                // 判断题用识别字母精确比对（更可靠）；填空题用模型判等 + 归一化精确匹配兜底
                if ("SINGLE_CHOICE".equals(q.getType())) {
                    ok = ocr.answer() != null
                            && ocr.answer().trim().equalsIgnoreCase(q.getAnswer().trim());
                } else {
                    ok = ocr.correct() || norm(ocr.answer()).equals(norm(q.getAnswer()));
                }
                String head = (ocr.error() != null) ? "[图片识别失败] " : "[图片识别] ";
                finalUserAnswer = head + (ocr.answer() == null ? "" : ocr.answer());
            } else {
                ok = grade(q, a.answer());
                finalUserAnswer = norm(a.answer());
            }
            if (ok) correct++;

            // 该题涉及的知识点都要更新掌握度
            for (String pc : splitCodes(q.getPointCodes())) {
                updateMastery(studentId, pc, ok);
            }

            // 落库「做题历史」：每条记录带标准答案，供历史做题记录展示
            AnswerHistory history = new AnswerHistory();
            history.setStudentId(studentId);
            history.setQuestionCode(q.getCode());
            history.setUserAnswer(finalUserAnswer);
            history.setCorrectAnswer(q.getAnswer());
            history.setIsCorrect(ok ? 1 : 0);
            answerHistoryMapper.insert(history);

            feedback.add(new AssessmentResult.QuestionFeedback(
                    q.getCode(), ok, finalUserAnswer,
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

    /**
     * 调用大模型，把规则计划 + 诊断润色成更贴近个人的专属解读。
     * 这是「大模型集成」与「AI 安全可控」的体现：规则计划永远兜底，AI 仅做语言润色，
     * 失败时返回 null，由前端继续展示规则计划（不拖垮核心功能）。
     */
    public String generateAiPlan(String studentId) {
        List<AssessmentResult.PointDiagnosis> diagnosis = buildDiagnosis(studentId);
        AssessmentResult.StudyPlan rulePlan = planService.buildPlan(diagnosis);

        StringBuilder sb = new StringBuilder();
        sb.append("你是考研备考规划助手。下面是某位同学的数学学情诊断（BKT 算法得出）和三阶段规则计划。\n");
        sb.append("请基于这些信息，用中文写一段 220 字以内的个性化备考建议：语气鼓励、具体、可执行；");
        sb.append("重点针对薄弱知识点给出起步动作；不要编造数据，不要超出已知信息。\n\n");
        sb.append("【知识点掌握度（0~1，0.4 以下为薄弱）】\n");
        for (AssessmentResult.PointDiagnosis d : diagnosis) {
            sb.append("- ").append(d.pointName()).append("：掌握度 ").append(d.mastery()).append("（").append(d.level()).append("）\n");
        }
        sb.append("\n【规则计划摘要】").append(rulePlan.summary()).append("\n");
        for (AssessmentResult.PhasePlan ph : rulePlan.phases()) {
            sb.append(ph.name()).append("（").append(ph.weeks()).append("）：").append(ph.focus()).append("\n");
        }

        String system = "你是专业、友好、严谨的考研备考规划助手，只基于给定信息回答，不编造任何数据。";
        try {
            return chatClient.prompt()
                    .system(system)
                    .user(sb.toString())
                    .call()
                    .content();
        } catch (Exception e) {
            // 大模型限流或故障时返回 null，前端展示规则计划兜底
            return null;
        }
    }

    /**
     * 查询某学生的做题历史（含每题标准答案），按提交时间倒序。
     * 用于「历史做题记录」展示：每条记录都附带对应题目的标准答案。
     */
    public List<AssessmentResult.HistoryItem> getHistory(String studentId) {
        List<AnswerHistory> list = answerHistoryMapper.selectList(new LambdaQueryWrapper<AnswerHistory>()
                .eq(AnswerHistory::getStudentId, studentId)
                .orderByDesc(AnswerHistory::getCreatedAt));
        if (list.isEmpty()) return List.of();

        // 一次性把所有题目题干取出，避免 N 条 SQL
        Map<String, String> stemMap = questionMapper.selectList(null).stream()
                .collect(Collectors.toMap(Question::getCode, Question::getStem, (a, b) -> a));

        List<AssessmentResult.HistoryItem> items = new ArrayList<>();
        for (AnswerHistory h : list) {
            String created = (h.getCreatedAt() != null)
                    ? h.getCreatedAt().toString().replace("T", " ").substring(0, 16)
                    : "";
            items.add(new AssessmentResult.HistoryItem(
                    h.getQuestionCode(),
                    stemMap.getOrDefault(h.getQuestionCode(), ""),
                    h.getUserAnswer(),
                    h.getCorrectAnswer(),
                    h.getIsCorrect() != null && h.getIsCorrect() == 1,
                    created));
        }
        return items;
    }

    /**
     * 清除某学生的全部历史学习数据：掌握度（mastery）+ 做题历史（answer_history）。
     * 清除后该学生回到「未测评」状态（诊断仅显示先验值）。
     */
    public void clearData(String studentId) {
        masteryMapper.delete(new LambdaQueryWrapper<Mastery>()
                .eq(Mastery::getStudentId, studentId));
        answerHistoryMapper.delete(new LambdaQueryWrapper<AnswerHistory>()
                .eq(AnswerHistory::getStudentId, studentId));
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
