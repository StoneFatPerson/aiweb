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
 * 测评业务编排：把「抽题 → 判分 → BKT 诊断」串成一条闭环。
 * 位置：src/main/java/com/aiweb/service/AssessmentService.java
 *
 * 这是「学情诊断 + 题库接入」的大脑：
 *   start   —— 按学情抽题（不含答案）
 *   submit  —— 收答案、判分、更新各知识点掌握度、出诊断；
 *              判错的题自动收录进错题本（WrongQuestionService）
 */
@Service
public class AssessmentService {

    /** 支持的科目代码 */
    public static final List<String> SUBJECTS = List.of("math", "english", "politics");

    private final QuestionMapper questionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final MasteryMapper masteryMapper;
    private final AnswerHistoryMapper answerHistoryMapper;
    private final DiagnosisService diagnosisService;
    private final WrongQuestionService wrongQuestionService;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final OcrService ocrService;

    public AssessmentService(QuestionMapper questionMapper,
                             KnowledgePointMapper knowledgePointMapper,
                             MasteryMapper masteryMapper,
                             AnswerHistoryMapper answerHistoryMapper,
                             DiagnosisService diagnosisService,
                             WrongQuestionService wrongQuestionService,
                             ObjectMapper objectMapper,
                             ChatClient.Builder chatClientBuilder,
                             OcrService ocrService) {
        this.questionMapper = questionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.masteryMapper = masteryMapper;
        this.answerHistoryMapper = answerHistoryMapper;
        this.diagnosisService = diagnosisService;
        this.wrongQuestionService = wrongQuestionService;
        this.objectMapper = objectMapper;
        this.chatClient = chatClientBuilder.build();
        this.ocrService = ocrService;
    }

    /** 开始测评：按科目抽取题目返回（不含答案） */
    public AssessmentStartResponse start(String studentId, String subject, String targetMath, int count) {
        // 科目代码：不传默认 math（兼容旧前端），并防止非法值穿透
        String subj = (subject == null || subject.isBlank()) ? "math" : subject.trim().toLowerCase();
        if (!SUBJECTS.contains(subj)) subj = "math";

        // 按科目取题（英语/政治的 applicableTo 为空串，卷种筛选自然不过滤）
        List<Question> questions = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getSubjectCode, subj));

        // 按目标数学类型筛选（所有种子题都适用于 math1/2/3，这里保留逻辑以备扩充）
        if ("math".equals(subj) && targetMath != null && !targetMath.isBlank() && !"all".equalsIgnoreCase(targetMath)) {
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

        int totalPoints = Math.toIntExact(knowledgePointMapper.selectCount(new LambdaQueryWrapper<KnowledgePoint>()
                .likeRight(KnowledgePoint::getCode, prefixOf(subj))));
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
            if (ok) {
                correct++;
            } else {
                // 判错：自动收录进错题本（幂等，重复答错同一题只更新不堆记录）
                try {
                    wrongQuestionService.recordSystemWrong(studentId, q, finalUserAnswer);
                } catch (Exception e) {
                    // 错题本失败不拖垮诊断主流程
                    System.err.println("[错题本] 自动记录失败：" + e.getMessage());
                }
            }

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

        // 本次提交的题目同属一个科目（前端按科目抽题），取第一题的科目作诊断维度
        String subject = "math";
        for (AssessmentSubmitRequest.AnswerSubmission a : answers) {
            Question q = questionMapper.selectOne(
                    new LambdaQueryWrapper<Question>().eq(Question::getCode, a.questionCode()));
            if (q != null && q.getSubjectCode() != null) { subject = q.getSubjectCode(); break; }
        }

        List<AssessmentResult.PointDiagnosis> diagnosis = buildDiagnosis(studentId, subject);
        List<String> weak = diagnosis.stream()
                .filter(d -> d.mastery() < 0.4)
                .map(AssessmentResult.PointDiagnosis::pointName)
                .collect(Collectors.toList());

        return new AssessmentResult(total, correct, score, feedback, diagnosis, weak);
    }

    /** 仅查看某学生的诊断（不重新答题），按科目查看 */
    public AssessmentResult.DiagnosisView getDiagnosis(String studentId, String subject) {
        List<AssessmentResult.PointDiagnosis> diagnosis = buildDiagnosis(studentId, subject);
        List<String> weak = diagnosis.stream()
                .filter(d -> d.mastery() < 0.4)
                .map(AssessmentResult.PointDiagnosis::pointName)
                .collect(Collectors.toList());
        // 是否真正做过测评：掌握度表里该学生在该科目下有记录才算「已测评」，否则只是先验值
        long assessedCount = masteryMapper.selectCount(new LambdaQueryWrapper<Mastery>()
                .eq(Mastery::getStudentId, studentId)
                .likeRight(Mastery::getPointCode, prefixOf(subject)));
        boolean assessed = assessedCount > 0;
        return new AssessmentResult.DiagnosisView(diagnosis, weak, assessed);
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

    public List<KnowledgePoint> allPoints(String subject) {
        return knowledgePointMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                .likeRight(KnowledgePoint::getCode, prefixOf(subject)));
    }

    /** 题库统计：总量 + 各科目分别统计（前端按学科展示） */
    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("questionCount", questionMapper.selectCount(null));
        m.put("pointCount", knowledgePointMapper.selectCount(null));
        Map<String, Object> bySubject = new HashMap<>();
        for (String s : SUBJECTS) {
            Map<String, Object> one = new HashMap<>();
            one.put("questionCount", questionMapper.selectCount(new LambdaQueryWrapper<Question>()
                    .eq(Question::getSubjectCode, s)));
            one.put("pointCount", knowledgePointMapper.selectCount(new LambdaQueryWrapper<KnowledgePoint>()
                    .likeRight(KnowledgePoint::getCode, prefixOf(s))));
            bySubject.put(s, one);
        }
        m.put("bySubject", bySubject);
        return m;
    }

    /** 科目代码 -> 知识点 code 前缀（math -> MATH-） */
    private String prefixOf(String subject) {
        return switch (subject) {
            case "math" -> "MATH-";
            case "english" -> "ENGLISH-";
            case "politics" -> "POLITICS-";
            default -> "MATH-";
        };
    }

    /** 科目代码 -> 中文名（用于提示词与展示） */
    private String subjectName(String subject) {
        return switch (subject) {
            case "math" -> "数学";
            case "english" -> "英语";
            case "politics" -> "政治";
            default -> "数学";
        };
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

    /** 判分：选择题大小写不敏感，填空题做归一化（去空格、转小写）。包级可见以便单元测试 */
    boolean grade(Question q, String userAnswer) {
        if (userAnswer == null) return false;
        String u = norm(userAnswer);
        String a = norm(q.getAnswer());
        if ("FILL_BLANK".equals(q.getType())) {
            return u.equals(a);
        }
        return u.equalsIgnoreCase(a);
    }

    /** 答案归一化：去空白、转小写，避免「 c 」与「C」被判错。包级可见以便单元测试 */
    String norm(String s) {
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

    /** 构造某学生某科目的全量知识点诊断（未作答的知识点用先验 0.3） */
    private List<AssessmentResult.PointDiagnosis> buildDiagnosis(String studentId, String subject) {
        List<KnowledgePoint> points = knowledgePointMapper.selectList(new LambdaQueryWrapper<KnowledgePoint>()
                .likeRight(KnowledgePoint::getCode, prefixOf(subject)));
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
