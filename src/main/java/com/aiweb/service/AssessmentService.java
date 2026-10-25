package com.aiweb.service;

import com.aiweb.dto.AssessmentResult;
import com.aiweb.dto.AssessmentStartRequest;
import com.aiweb.dto.AssessmentStartResponse;
import com.aiweb.dto.AssessmentSubmitRequest;
import com.aiweb.entity.AnswerHistory;
import com.aiweb.entity.KnowledgePoint;
import com.aiweb.entity.Mastery;
import com.aiweb.entity.Question;
import com.aiweb.entity.WrongQuestion;
import com.aiweb.mapper.AnswerHistoryMapper;
import com.aiweb.mapper.KnowledgePointMapper;
import com.aiweb.mapper.MasteryMapper;
import com.aiweb.mapper.QuestionMapper;
import com.aiweb.mapper.WrongQuestionMapper;
import com.aiweb.service.OcrService.OcrResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /** 单张答案图片的 base64 上限（约 6MB 原图）：防内存放大与 DoS */
    public static final int MAX_IMAGE_BASE64_LEN = 8 * 1024 * 1024;

    private final QuestionMapper questionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final MasteryMapper masteryMapper;
    private final AnswerHistoryMapper answerHistoryMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final DiagnosisService diagnosisService;
    private final WrongQuestionService wrongQuestionService;
    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final OcrService ocrService;

    public AssessmentService(QuestionMapper questionMapper,
                             KnowledgePointMapper knowledgePointMapper,
                             MasteryMapper masteryMapper,
                             AnswerHistoryMapper answerHistoryMapper,
                             WrongQuestionMapper wrongQuestionMapper,
                             DiagnosisService diagnosisService,
                             WrongQuestionService wrongQuestionService,
                             ObjectMapper objectMapper,
                             ChatClient.Builder chatClientBuilder,
                             OcrService ocrService) {
        this.questionMapper = questionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.masteryMapper = masteryMapper;
        this.answerHistoryMapper = answerHistoryMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
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

    /**
     * 提交答案：判分 + 更新掌握度 + 出诊断。
     * 判错的题自动收录进错题本。
     *
     * 防护（踩过的坑，见 docs/测试报告.md）：
     *   - answers 为 null / 超长（> 50）直接拒绝，防 NPE 与「批量带图提交」放大攻击；
     *   - 单张图片 base64 上限 8MB，防内存放大；
     *   - OCR 识别失败（限流/图片模糊）不计为答错——否则会冤枉拉低 BKT、污染错题本；
     *   - 单题异常（含 BKT/历史写入）不中断整轮，逐题隔离；整轮加事务保证要么基本完整要么整体回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public AssessmentResult submit(String studentId, List<AssessmentSubmitRequest.AnswerSubmission> answers) {
        if (answers == null || answers.isEmpty()) {
            throw new IllegalArgumentException("提交的答案不能为空");
        }
        if (answers.size() > 50) {
            throw new IllegalArgumentException("单次提交最多 50 题");
        }

        List<AssessmentResult.QuestionFeedback> feedback = new ArrayList<>();
        int correct = 0;
        int invalid = 0;

        for (AssessmentSubmitRequest.AnswerSubmission a : answers) {
            Question q = questionMapper.selectOne(
                    new LambdaQueryWrapper<Question>().eq(Question::getCode, a.questionCode()));
            if (q == null) continue;

            boolean ok = false;
            boolean invalidThis = false;          // 本题无法评分（OCR 失败 / AI 不可用）：不计入得分、不污染掌握度
            String finalUserAnswer;
            String scoringMode = scoringModeOf(q.getType());
            Integer aiScore = null;
            String aiComment = null;

            if (isAiScored(q.getType())) {
                // ===== 主观题：交给大模型评分（大题/作文/翻译/简答）=====
                String text = (a.answer() == null) ? "" : a.answer();
                // 仅给图片时，先用 OCR 抽出文字，再交给 AI 评判
                if (text.isBlank() && a.imageBase64() != null && !a.imageBase64().isBlank()) {
                    if (a.imageBase64().length() > MAX_IMAGE_BASE64_LEN) {
                        throw new IllegalArgumentException("答案图片过大（上限约 6MB），请压缩后重试");
                    }
                    byte[] img;
                    try {
                        img = Base64.getDecoder().decode(a.imageBase64());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("图片数据格式不正确");
                    }
                    OcrResult ocr = ocrService.recognize(img, a.imageMime(), "", q.getType());
                    if (ocr.error() != null) {
                        invalidThis = true;
                        aiComment = "图片无法识别，请改用文字输入或重传。";
                        finalUserAnswer = "[图片识别失败] " + ocr.error();
                    } else {
                        text = ocr.answer() == null ? "" : ocr.answer();
                        finalUserAnswer = "[图片识别] " + text;
                    }
                } else {
                    finalUserAnswer = text;       // 保留原文（含大小写/换行），便于展示
                }

                if (!invalidThis) {
                    AiGradeResult g = aiGrade(q, text);
                    if (g.unavailable()) {
                        // AI 通道故障（限流/网络）≠ 学生答错：标记待复核，跳过 BKT/历史/错题本
                        invalidThis = true;
                        aiComment = g.comment();
                        finalUserAnswer = finalUserAnswer + " [AI 评分暂不可用，已标记为待复核]";
                    } else {
                        aiScore = g.score();
                        aiComment = g.comment();
                        ok = aiScore >= AI_PASS_SCORE;   // 达标记为「答对」，用于 BKT
                    }
                }
            } else {
                // ===== 客观题：沿用 OCR 图片 / 文字判分两套逻辑 =====
                boolean fromImage = a.imageBase64() != null && !a.imageBase64().isBlank();
                if (fromImage) {
                    if (a.imageBase64().length() > MAX_IMAGE_BASE64_LEN) {
                        throw new IllegalArgumentException("答案图片过大（上限约 6MB），请压缩后重试");
                    }
                    byte[] imageBytes;
                    try {
                        imageBytes = Base64.getDecoder().decode(a.imageBase64());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("图片数据格式不正确");
                    }
                    OcrResult ocr = ocrService.recognize(imageBytes, a.imageMime(), q.getAnswer(), q.getType());
                    if (ocr.error() != null) {
                        invalidThis = true;
                        finalUserAnswer = "[图片识别失败] " + ocr.error();
                    } else {
                        if (isChoiceLike(q.getType())) {
                            ok = ocr.answer() != null
                                    && ocr.answer().trim().equalsIgnoreCase(q.getAnswer().trim());
                        } else {
                            ok = ocr.correct() || norm(ocr.answer()).equals(norm(q.getAnswer()));
                        }
                        finalUserAnswer = "[图片识别] " + (ocr.answer() == null ? "" : ocr.answer());
                    }
                } else {
                    ok = grade(q, a.answer());
                    finalUserAnswer = norm(a.answer());
                }
            }

            if (invalidThis) {
                // 无法评分的题：只记录反馈（标记不计分），不动 BKT/错题本/历史对错
                invalid++;
                feedback.add(new AssessmentResult.QuestionFeedback(
                        q.getCode(), q.getStem(), false, finalUserAnswer,
                        q.getAnswer(), aiComment != null ? aiComment : q.getExplanation(),
                        splitCodes(q.getPointCodes()), scoringMode, aiScore, aiComment));
                continue;
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

            // 该题涉及的知识点都要更新掌握度（单题异常不中断整轮）
            for (String pc : splitCodes(q.getPointCodes())) {
                try {
                    updateMastery(studentId, pc, ok);
                } catch (Exception e) {
                    System.err.println("[诊断] 掌握度更新失败（" + pc + "）：" + e.getMessage());
                }
            }

            // 落库「做题历史」：每条记录带标准答案，供历史做题记录展示
            try {
                AnswerHistory history = new AnswerHistory();
                history.setStudentId(studentId);
                history.setQuestionCode(q.getCode());
                history.setUserAnswer(finalUserAnswer);
                history.setCorrectAnswer(q.getAnswer());
                history.setIsCorrect(ok ? 1 : 0);
                answerHistoryMapper.insert(history);
            } catch (Exception e) {
                System.err.println("[诊断] 做题历史写入失败：" + e.getMessage());
            }

            feedback.add(new AssessmentResult.QuestionFeedback(
                    q.getCode(), q.getStem(), ok, finalUserAnswer,
                    q.getAnswer(), q.getExplanation(), splitCodes(q.getPointCodes()),
                    scoringMode, aiScore, aiComment));
        }

        int total = answers.size() - invalid; // 总分母剔除识别失败的题，不冤枉学生
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
    /**
     * 清除某学生的全部历史学习数据：掌握度 + 做题历史 + 错题本。
     * 三者一起清才与「全部」的语义对齐（原来不清错题本，学生清了历史诊断回到未测评，
     * 错题本却还剩 N 题，自相矛盾）。加事务保证要么全清要么不清。
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearData(String studentId) {
        masteryMapper.delete(new LambdaQueryWrapper<Mastery>()
                .eq(Mastery::getStudentId, studentId));
        answerHistoryMapper.delete(new LambdaQueryWrapper<AnswerHistory>()
                .eq(AnswerHistory::getStudentId, studentId));
        wrongQuestionMapper.delete(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getStudentId, studentId));
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
        if (isChoiceLike(q.getType()) && q.getOptions() != null) {
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

    /** 选择题类题型（系统客观判分，用选项 key 精确比对） */
    private static final Set<String> CHOICE_LIKE = Set.of("SINGLE_CHOICE", "CLOZE", "READING");
    /** 主观题（交给大模型评分） */
    private static final Set<String> AI_SCORED = Set.of("ESSAY", "COMPOSITION", "TRANSLATION", "SHORT_ANSWER");
    /** AI 评分达标阈值：达到该比例记为「达标」，用于 BKT 掌握度更新 */
    private static final int AI_PASS_SCORE = 60;

    /** 判分：选择题类大小写不敏感，其余（填空等）做归一化精确匹配。包级可见以便单元测试 */
    boolean grade(Question q, String userAnswer) {
        if (userAnswer == null) return false;
        String u = norm(userAnswer);
        String a = norm(q.getAnswer());
        if (isChoiceLike(q.getType())) {
            return u.equalsIgnoreCase(a);
        }
        // 填空题等：归一化后精确比对（去空白、转小写）
        return u.equals(a);
    }

    private boolean isChoiceLike(String type) {
        return type != null && CHOICE_LIKE.contains(type);
    }

    private boolean isAiScored(String type) {
        return type != null && AI_SCORED.contains(type);
    }

    /** 题型 -> 评分方式：AI / AUTO */
    private String scoringModeOf(String type) {
        return isAiScored(type) ? "AI" : "AUTO";
    }

    /** 题型 -> 中文名（用于 AI 评分提示词） */
    private String typeName(String type) {
        return switch (type == null ? "" : type) {
            case "SINGLE_CHOICE" -> "单选题";
            case "FILL_BLANK" -> "填空题";
            case "CLOZE" -> "完形填空";
            case "READING" -> "阅读理解";
            case "ESSAY" -> "数学解答题";
            case "COMPOSITION" -> "英语作文";
            case "TRANSLATION" -> "翻译题";
            case "SHORT_ANSWER" -> "简答题";
            default -> "客观题";
        };
    }

    /** 答案归一化：去空白、转小写，避免「 c 」与「C」被判错。包级可见以便单元测试 */
    String norm(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("[\\s　]", "").toLowerCase();
    }

    /**
     * 主观题（大题/作文/翻译/简答）的大模型评分结果。
     * unavailable=true 表示 AI 调用失败（限流/网络），此时本题不计入得分、不更新掌握度。
     */
    private record AiGradeResult(Integer score, boolean correct, String comment, boolean unavailable) {
        /** 正常结果（未标记不可用） */
        AiGradeResult(Integer score, boolean correct, String comment) {
            this(score, correct, comment, false);
        }
        /** 降级结果：AI 不可用 / 解析失败 */
        AiGradeResult(String comment, boolean unavailable) {
            this(null, false, comment, unavailable);
        }
    }

    /**
     * 调用智谱 GLM 给主观题打分。健壮性设计（与 OCR / 对话一致的降级策略）：
     *   - 限流/网络异常被捕获，返回 unavailable，主流程降级为「待复核」，绝不 500；
     *   - 要求模型只返回 JSON，解析失败同样降级，不污染掌握度与错题本；
     *   - 评分口径：覆盖主要得分点给 60 分及以上记为「达标」，严重偏离/空白给 60 分以下。
     */
    private AiGradeResult aiGrade(Question q, String studentAnswer) {
        if (studentAnswer == null || studentAnswer.isBlank()) {
            return new AiGradeResult(0, false, "作答为空，未得分。", false);
        }
        String sys = "你是严谨的考研阅卷老师，负责给主观题打分。请依据【参考答案/评分要点】评估学生作答的" +
                "要点覆盖、逻辑与表述，给出 0-100 的整数分。" +
                "达到主要得分点给 60 分及以上（记为达标 correct=true），严重偏离题意或空白给 60 分以下（correct=false）。" +
                "只返回一个 JSON 对象，不要包含任何额外文字或 markdown 代码块，格式严格为：" +
                "{\"score\":<0到100的整数>,\"correct\":<true或false>,\"comment\":\"<中文简评，指出优点或不足，不超过60字>\"}";

        String user = "【科目】" + subjectName(q.getSubjectCode()) + "\n" +
                "【题型】" + typeName(q.getType()) + "\n" +
                "【题目】" + (q.getStem() == null ? "" : q.getStem()) + "\n" +
                "【参考答案/评分要点】" + (q.getAnswer() == null ? "" : q.getAnswer()) + "\n" +
                "【学生作答】" + studentAnswer + "\n" +
                "请严格只返回上面的 JSON。";

        try {
            String resp = chatClient.prompt()
                    .system(sys)
                    .user(user)
                    .call()
                    .content();
            return parseAi(resp);
        } catch (Exception e) {
            return new AiGradeResult("AI 评分暂不可用（免费接口限流或网络波动），本题已标记为待复核，不影响客观题得分。", true);
        }
    }

    /** 从模型返回里稳妥取出 score / correct / comment（容错，失败即降级） */
    private AiGradeResult parseAi(String raw) {
        if (raw == null) return new AiGradeResult("AI 评分结果解析失败，已标记为待复核。", true);
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("^```[a-zA-Z]*", "").replaceAll("```$", "").trim();
        }
        Matcher block = Pattern.compile("\\{.*\\}", Pattern.DOTALL).matcher(s);
        if (block.find()) s = block.group();
        try {
            String scoreStr = extractJsonField(s, "score");
            String comment = extractJsonField(s, "comment");
            // 兼容整数与小数（如 85 / 85.0），四舍五入成 0-100 整数
            int score = (int) Math.round(Double.parseDouble(scoreStr.trim()));
            if (score < 0) score = 0;
            if (score > 100) score = 100;
            // 是否「达标」统一以阈值判定，避免模型漏给/错给 correct 字段
            boolean correct = score >= AI_PASS_SCORE;
            return new AiGradeResult(score, correct, comment == null ? "" : comment, false);
        } catch (Exception e) {
            return new AiGradeResult("AI 评分结果解析失败，已标记为待复核。", true);
        }
    }

    /** 简化的 JSON 字段提取，避免严格类型绑定带来的脆弱性 */
    private String extractJsonField(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\"([^\"]*)\"|true|false|-?\\d+)")
                .matcher(json);
        if (m.find()) {
            String g = m.group(1);
            if (g.startsWith("\"")) return g.substring(1, g.length() - 1);
            return g;
        }
        return "";
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
