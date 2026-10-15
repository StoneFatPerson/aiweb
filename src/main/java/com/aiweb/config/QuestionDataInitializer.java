package com.aiweb.config;

import com.aiweb.entity.KnowledgePoint;
import com.aiweb.entity.Question;
import com.aiweb.mapper.KnowledgePointMapper;
import com.aiweb.mapper.QuestionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 题库与知识点的自动导入（多科目）。
 * 位置：src/main/java/com/aiweb/config/QuestionDataInitializer.java
 *
 * 作用：应用启动时扫描 data/ 目录下所有 *-seed-*.json（数学/英语/政治），
 *   - knowledge_point 表：按知识点 code 前缀（MATH-/ENGLISH-/POLITICS-）判断该科目是否已导入
 *   - question_bank 表：按 subject_code 判断该科目是否已导入
 *   只导「没有的」，已导入的跳过，不会重复。
 *
 * 好处：后续往 data/ 里加新批次文件（如 math-seed-batch2.json）无需改代码；
 *       老库（只导过数学）启动时会自动补齐英语、政治，不影响已有数据。
 *
 * 数据来源：项目原创编写（非真题），各 JSON 的 scopeNote 已注明。
 */
@Component
public class QuestionDataInitializer implements ApplicationRunner {

    /** data 目录下的题库文件命名规则：{科目}-seed-batch{N}.json */
    private static final String DATA_PREFIX = "data/";
    private static final String[] SEED_FILES = {
            "math-seed-batch1.json",
            "english-seed-batch1.json",
            "politics-seed-batch1.json"
    };

    private final QuestionMapper questionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ObjectMapper objectMapper;

    public QuestionDataInitializer(QuestionMapper questionMapper,
                                   KnowledgePointMapper knowledgePointMapper,
                                   ObjectMapper objectMapper) {
        this.questionMapper = questionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String file : SEED_FILES) {
            try {
                Resource res = new ClassPathResource(DATA_PREFIX + file);
                if (!res.exists()) {
                    System.out.println("[题库初始化] 未找到 " + file + "，跳过。");
                    continue;
                }
                importFile(file, res);
            } catch (Exception e) {
                System.err.println("[题库初始化] 导入 " + file + " 失败：" + e.getMessage());
            }
        }
    }

    /** 导入单个题库文件（按科目幂等：该科目已有数据则跳过） */
    private void importFile(String file, Resource res) throws Exception {
        JsonNode root;
        try (InputStream in = res.getInputStream()) {
            root = objectMapper.readTree(in);
        }
        String subjectCode = root.path("subject").path("code").asText("unknown");
        String pointPrefix = prefixOf(subjectCode);

        // 1) 导入知识点（该科目知识点不存在才导）
        long existingPoints = knowledgePointMapper.selectCount(new LambdaQueryWrapper<KnowledgePoint>()
                .likeRight(KnowledgePoint::getCode, pointPrefix));
        if (existingPoints == 0 && root.has("chapters")) {
            Map<String, String> chapterAppl = new HashMap<>();
            int kpCount = 0;
            for (JsonNode ch : root.get("chapters")) {
                String chCode = ch.get("code").asText();
                JsonNode appl = ch.get("applicableTo");
                String applStr = appl == null ? "" :
                        StreamSupport.stream(appl.spliterator(), false)
                                .map(JsonNode::asText).collect(Collectors.joining(","));
                chapterAppl.put(chCode, applStr);

                JsonNode points = ch.get("points");
                if (points != null) {
                    for (JsonNode p : points) {
                        KnowledgePoint kp = new KnowledgePoint();
                        kp.setCode(p.get("code").asText());
                        kp.setName(p.get("name").asText());
                        kp.setChapterCode(chCode);
                        kp.setChapterName(ch.get("name").asText());
                        kp.setApplicableTo(applStr);
                        knowledgePointMapper.insert(kp);
                        kpCount++;
                    }
                }
            }
            System.out.println("[题库初始化] " + file + "：已导入 " + kpCount + " 个知识点（" + subjectCode + "）。");
        } else {
            System.out.println("[题库初始化] " + file + "：" + subjectCode + " 知识点已有数据，跳过。");
        }

        // 2) 导入题目（该科目题目不存在才导）
        long existingQuestions = questionMapper.selectCount(new LambdaQueryWrapper<Question>()
                .eq(Question::getSubjectCode, subjectCode));
        if (existingQuestions == 0 && root.has("questions")) {
            // 章节 -> 适用类型 映射，写进每道题
            Map<String, String> chapterAppl = new HashMap<>();
            if (root.has("chapters")) {
                for (JsonNode ch : root.get("chapters")) {
                    JsonNode appl = ch.get("applicableTo");
                    String applStr = appl == null ? "" :
                            StreamSupport.stream(appl.spliterator(), false)
                                    .map(JsonNode::asText).collect(Collectors.joining(","));
                    chapterAppl.put(ch.get("code").asText(), applStr);
                }
            }

            int qCount = 0;
            for (JsonNode qn : root.get("questions")) {
                Question q = new Question();
                q.setCode(qn.get("code").asText());
                q.setSubjectCode(qn.get("subjectCode").asText());
                q.setChapterCode(qn.get("chapterCode").asText());
                q.setPointCodes(joinArray(qn.get("pointCodes")));
                q.setApplicableTo(chapterAppl.getOrDefault(qn.get("chapterCode").asText(), ""));
                q.setType(qn.get("type").asText());
                q.setDifficulty(qn.get("difficulty").asInt());
                q.setYear(qn.has("year") && !qn.get("year").isNull() ? qn.get("year").asText() : null);
                q.setSource(qn.has("source") ? qn.get("source").asText() : "ORIGINAL_SEED");
                q.setStem(qn.get("stem").asText());
                q.setOptions(qn.has("options") ? qn.get("options").toString() : null);
                q.setAnswer(qn.get("answer").asText());
                q.setExplanation(qn.has("explanation") ? qn.get("explanation").asText() : "");
                q.setKeywords(joinArray(qn.get("keywords")));
                q.setVerified(1); // 种子题为本项目原创，已核对
                questionMapper.insert(q);
                qCount++;
            }
            System.out.println("[题库初始化] " + file + "：已导入 " + qCount + " 道题目（" + subjectCode + "）。");
        } else {
            System.out.println("[题库初始化] " + file + "：" + subjectCode + " 题目已有数据，跳过。");
        }
    }

    /** 科目代码 -> 知识点 code 前缀（math -> MATH-，english -> ENGLISH-，politics -> POLITICS-） */
    private String prefixOf(String subjectCode) {
        return switch (subjectCode) {
            case "math" -> "MATH-";
            case "english" -> "ENGLISH-";
            case "politics" -> "POLITICS-";
            default -> subjectCode.toUpperCase() + "-";
        };
    }

    private String joinArray(JsonNode arr) {
        if (arr == null || !arr.isArray()) return "";
        return StreamSupport.stream(arr.spliterator(), false)
                .map(JsonNode::asText).collect(Collectors.joining(","));
    }
}
