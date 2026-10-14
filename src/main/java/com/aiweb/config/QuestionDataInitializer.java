package com.aiweb.config;

import com.aiweb.entity.KnowledgePoint;
import com.aiweb.entity.Question;
import com.aiweb.mapper.KnowledgePointMapper;
import com.aiweb.mapper.QuestionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 数学题库与知识点的自动导入。
 * 位置：src/main/java/com/aiweb/config/QuestionDataInitializer.java
 *
 * 作用：应用启动时读取 data/math-seed-batch1.json，
 *   - knowledge_point 表空才导入知识点（取自 JSON 的 chapters/points）
 *   - question_bank 表空才导入题目
 * 好处：你不用手动执行任何 SQL，启动即可用；已导入则跳过，不会重复。
 *
 * 数据来源：项目原创编写（非真题），scopeNote 已注明。
 */
@Component
public class QuestionDataInitializer implements ApplicationRunner {

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
        try {
            JsonNode root = readJson();

            // 1) 导入知识点（表空才导）
            if (knowledgePointMapper.selectCount(null) == 0 && root.has("chapters")) {
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
                System.out.println("[题库初始化] 已导入 " + kpCount + " 个知识点。");
            } else {
                System.out.println("[题库初始化] knowledge_point 已有数据，跳过知识点导入。");
            }

            // 2) 导入题目（表空才导）
            if (questionMapper.selectCount(null) == 0 && root.has("questions")) {
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
                System.out.println("[题库初始化] 已导入 " + qCount + " 道题目。");
            } else {
                System.out.println("[题库初始化] question_bank 已有数据，跳过题目导入。");
            }

        } catch (Exception e) {
            System.err.println("[题库初始化] 导入失败：" + e.getMessage());
        }
    }

    private JsonNode readJson() throws Exception {
        try (InputStream in = new ClassPathResource("data/math-seed-batch1.json").getInputStream()) {
            return objectMapper.readTree(in);
        }
    }

    private String joinArray(JsonNode arr) {
        if (arr == null || !arr.isArray()) return "";
        return StreamSupport.stream(arr.spliterator(), false)
                .map(JsonNode::asText).collect(Collectors.joining(","));
    }
}
