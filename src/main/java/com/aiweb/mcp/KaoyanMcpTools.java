package com.aiweb.mcp;

import com.aiweb.dto.AssessmentResult;
import com.aiweb.entity.College;
import com.aiweb.entity.Major;
import com.aiweb.service.AssessmentService;
import com.aiweb.service.CollegeService;
import com.aiweb.service.RagService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP 工具集：把本项目已有能力按 MCP（Model Context Protocol，模型上下文协议）暴露出去。
 * 位置：src/main/java/com/aiweb/mcp/KaoyanMcpTools.java
 *
 * 作用：任何支持 MCP 的 AI 客户端（Cherry Studio / Claude Desktop / Cline 等）
 * 连接本服务（http://localhost:8080/sse）后，就能在对话中直接调用这些工具
 * 查询院校专业、咨询考研知识、查看学情诊断。
 *
 * 注册方式：方法上的 @McpTool 注解由 MCP server starter 自带的注解扫描器
 * （McpServerAnnotationScannerAutoConfiguration）自动识别并暴露成 MCP 工具，
 * 不需要手写 ToolCallbackProvider——这样也避免了与 Spring AI ToolCalling
 * 自动配置相互收集而产生的循环依赖。
 *
 * 设计原则与全项目一致：
 *   - 事实全部来自数据库 / 本地知识库（RAG 检索），不靠模型现场编造；
 *   - 返回结构化、限量的中文文本，控制上下文体积；
 *   - 工具描述（description）就是给客户端大模型看的「使用说明书」，必须写清楚用途与参数含义。
 */
@Component
public class KaoyanMcpTools {

    private final CollegeService collegeService;
    private final RagService ragService;
    private final AssessmentService assessmentService;

    /**
     * 构造注入三个 Service。
     * 注意：本类只被 MCP server 的注解扫描器（BeanPostProcessor）处理，
     * 不注册 ToolCallbackProvider bean，因此不会参与 Spring AI ToolCalling 自动配置的收集，
     * 也就不会与 chatClientBuilder 形成循环依赖。
     */
    public KaoyanMcpTools(CollegeService collegeService,
                          RagService ragService,
                          AssessmentService assessmentService) {
        this.collegeService = collegeService;
        this.ragService = ragService;
        this.assessmentService = assessmentService;
    }

    /**
     * 工具 1：查院校。
     * 数据源：MySQL 院校表（自行整理的结构化信息，以山东高校为主，不含分数线）。
     */
    @McpTool(name = "search_colleges",
            description = "查询考研院校库（以山东地区高校为主）。支持按城市、层次、是否985、是否211、关键词（院校全称或简称，如「山大」）组合筛选，返回院校名称、城市、层次与985/211/自划线标记。注意：库中不收录录取分数线，涉及分数的问题请引导用户查阅目标院校研究生院官网。")
    public String searchColleges(
            @McpToolParam(description = "所在城市，如「济南」「青岛」「烟台」，不填则不限", required = false) String city,
            @McpToolParam(description = "院校层次，如「双一流」，不填则不限", required = false) String level,
            @McpToolParam(description = "是否只查 985 院校，传 true/false，不填则不限", required = false) Boolean is985,
            @McpToolParam(description = "是否只查 211 院校，传 true/false，不填则不限", required = false) Boolean is211,
            @McpToolParam(description = "关键词，匹配院校全称或简称，如「山东大学」「山大」，不填则不限", required = false) String keyword,
            @McpToolParam(description = "最多返回几条结果，默认 20，最大 50", required = false) Integer maxResults) {
        int limit = normalizeLimit(maxResults, 20, 50);
        Integer f985 = (is985 != null && is985) ? 1 : null;
        Integer f211 = (is211 != null && is211) ? 1 : null;
        List<College> colleges = collegeService.listColleges(city, level, f985, f211, null, keyword);
        if (colleges.isEmpty()) {
            return "没有查询到符合条件的院校。可尝试放宽筛选条件（如去掉城市限制），或换一个关键词。";
        }
        boolean truncated = colleges.size() > limit;
        List<College> shown = colleges.subList(0, Math.min(limit, colleges.size()));
        StringBuilder sb = new StringBuilder();
        sb.append("共找到 ").append(colleges.size()).append("所院校");
        sb.append(truncated ? "，以下展示前 " + limit + " 所：\n" : "：\n");
        int i = 1;
        for (College c : shown) {
            // 注意不能叫 level：与入参 level 同名会编译报错
            String collegeLevel = nullToDash(c.getLevel());
            sb.append(i++).append(". ").append(c.getName())
                    .append("（").append(nullToDash(c.getCity()))
                    .append(" · ").append(collegeLevel);
            sb.append(tag(c.getIs985(), "985", collegeLevel))
                    .append(tag(c.getIs211(), "211", collegeLevel))
                    .append(tag(c.getIsSelfLine(), "自划线", collegeLevel))
                    .append("）\n");
        }
        return sb.toString();
    }

    /**
     * 工具 2：查某所院校的详情（含招生专业列表）。
     */
    @McpTool(name = "get_college_detail",
            description = "查询指定院校的详细信息，包括办学层次、所在城市、官网，以及该校招收的考研专业列表（专业名称、学位类型、所属门类、考试科目、学制）。需要先通过 search_colleges 或用户处获得院校 ID。")
    public String getCollegeDetail(
            @McpToolParam(description = "院校 ID（数字），从 search_colleges 的结果中获取") Long id) {
        College college = collegeService.getCollegeDetail(id);
        if (college == null) {
            return "未找到 ID 为 " + id + " 的院校，请先调用 search_colleges 获取正确的院校 ID。";
        }
        StringBuilder sb = new StringBuilder();
        String level = nullToDash(college.getLevel());
        sb.append("【").append(college.getName()).append("】\n")
                .append("所在城市：").append(nullToDash(college.getCity())).append("\n")
                .append("层次：").append(level)
                .append(tag(college.getIs985(), "985", level))
                .append(tag(college.getIs211(), "211", level))
                .append(tag(college.getIsSelfLine(), "自划线", level)).append("\n")
                .append("官网：").append(nullToDash(college.getWebsite())).append("\n");
        List<Major> majors = college.getMajors();
        if (majors == null || majors.isEmpty()) {
            sb.append("该院校暂无收录专业数据。");
            return sb.toString();
        }
        sb.append("\n招生专业（共 ").append(majors.size()).append(" 个）：\n");
        int i = 1;
        for (Major m : majors) {
            sb.append(i++).append(". ").append(m.getName())
                    .append("（").append(nullToDash(m.getDegreeType()))
                    .append(" · ").append(nullToDash(m.getCategory()));
            if (m.getDuration() != null) {
                sb.append(" · ").append(m.getDuration()).append("年制");
            }
            sb.append("）");
            if (m.getPublicCourses() != null && !m.getPublicCourses().isBlank()) {
                sb.append(" 考试科目：").append(m.getPublicCourses());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 工具 3：查专业。
     */
    @McpTool(name = "search_majors",
            description = "按条件查询考研专业库，支持按院校 ID、学科门类（如「工学」「管理学」）、学位类型（学术学位/专业学位）、关键词（专业名称或学科名）组合筛选，返回专业名称、开设院校、学位类型、门类、考试科目与学制。注意：不收录录取分数线与报录比。")
    public String searchMajors(
            @McpToolParam(description = "院校 ID，限定只在某所院校内查，不填则查全部院校", required = false) Long collegeId,
            @McpToolParam(description = "学科门类，如「工学」「理学」「管理学」，不填则不限", required = false) String category,
            @McpToolParam(description = "学位类型，如「学术学位」「专业学位」，不填则不限", required = false) String degreeType,
            @McpToolParam(description = "关键词，匹配专业名称或学科名，如「计算机」，不填则不限", required = false) String keyword,
            @McpToolParam(description = "最多返回几条结果，默认 20，最大 50", required = false) Integer maxResults) {
        int limit = normalizeLimit(maxResults, 20, 50);
        List<Major> majors = collegeService.listMajors(collegeId, category, degreeType, keyword);
        if (majors.isEmpty()) {
            return "没有查询到符合条件的专业。可尝试放宽筛选条件，或换一个关键词（如「计算机」而不是「计算机科学与技术」）。";
        }
        boolean truncated = majors.size() > limit;
        List<Major> shown = majors.subList(0, Math.min(limit, majors.size()));
        StringBuilder sb = new StringBuilder();
        sb.append("共找到 ").append(majors.size()).append(" 个专业");
        sb.append(truncated ? "，以下展示前 " + limit + " 个：\n" : "：\n");
        int i = 1;
        for (Major m : shown) {
            sb.append(i++).append(". ").append(m.getName())
                    .append("（").append(nullToDash(m.getCollegeName()))
                    .append(" · ").append(nullToDash(m.getDegreeType()))
                    .append(" · ").append(nullToDash(m.getCategory())).append("）");
            if (m.getPublicCourses() != null && !m.getPublicCourses().isBlank()) {
                sb.append(" 考试科目：").append(m.getPublicCourses());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 工具 4：考研知识咨询（RAG）。
     * 先向量检索自建考研知识库，再基于检索到的资料作答，答案附带参考来源。
     */
    @McpTool(name = "ask_kaoyan_knowledge",
            description = "咨询考研基础知识：流程与时间节点（报名/确认/初试/复试/调剂）、名词解释（学硕/专硕、A区/B区、自划线等）、考试科目（政治/英语/数学一二三/专业课）、备考建议。基于自建考研知识库检索作答并附参考来源；知识库没有的问题会明确说「没有相关信息」，不会编造。注意：不提供录取分数线。")
    public String askKaoyanKnowledge(
            @McpToolParam(description = "用户关于考研的具体问题，如「学硕和专硕有什么区别」「考研报名是什么时候」") String question) {
        if (question == null || question.isBlank()) {
            return "请提供具体的考研问题。";
        }
        RagService.RagAnswer answer = ragService.ask(question.trim());
        StringBuilder sb = new StringBuilder(answer.answer);
        if (answer.sources != null && !answer.sources.isEmpty()) {
            sb.append("\n\n参考来源：").append(String.join("；", answer.sources));
        }
        return sb.toString();
    }

    /**
     * 工具 5：学情诊断 + 备考计划。
     * 数据源：MySQL 作答记录 + BKT 掌握度模型 + 规则化计划生成，全部本地计算。
     */
    @McpTool(name = "get_study_diagnosis",
            description = "查看某位学生的学习情况诊断：各科目的知识点掌握度、薄弱点，以及据此生成的基础/强化/冲刺三阶段备考计划。需要学生先完成过至少一次测评（数学/英语/政治任一科目）；如果对方还没有测评记录，会提示先去做测评。")
    public String getStudyDiagnosis(
            @McpToolParam(description = "学生 ID（学号），由用户提供，如「demo」") String studentId,
            @McpToolParam(description = "科目代码：math / english / politics，不填默认 math", required = false) String subject) {
        if (studentId == null || studentId.isBlank()) {
            return "请提供学生 ID（学号）。";
        }
        String subj = (subject == null || subject.isBlank()) ? "math" : subject.trim().toLowerCase();
        if (!com.aiweb.service.AssessmentService.SUBJECTS.contains(subj)) subj = "math";
        String subjectName = switch (subj) {
            case "math" -> "数学";
            case "english" -> "英语";
            case "politics" -> "政治";
            default -> "数学";
        };
        AssessmentResult.DiagnosisView view = assessmentService.getDiagnosis(studentId.trim(), subj);
        if (view == null || !view.assessed()) {
            return "学号 " + studentId.trim() + " 暂无" + subjectName + "测评记录。请先完成一次测评（答一组题）后，再来查看诊断与备考计划。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【学情诊断】学生 ").append(studentId.trim()).append("（").append(subjectName).append("）\n\n");
        sb.append("各知识点掌握度：\n");
        if (view.diagnosis() != null) {
            for (AssessmentResult.PointDiagnosis d : view.diagnosis()) {
                sb.append("- ").append(d.pointName())
                        .append("：").append(Math.round(d.mastery() * 100)).append("%（")
                        .append(d.level()).append("）\n");
            }
        }
        if (view.weakPoints() != null && !view.weakPoints().isEmpty()) {
            sb.append("\n薄弱知识点：").append(String.join("、", view.weakPoints())).append("\n");
        }
        AssessmentResult.StudyPlan plan = view.plan();
        if (plan != null && plan.phases() != null && !plan.phases().isEmpty()) {
            sb.append("\n三阶段备考计划：\n");
            for (AssessmentResult.PhasePlan p : plan.phases()) {
                sb.append("■ ").append(p.name()).append("（约 ").append(nullToDash(p.weeks())).append("）\n")
                        .append("  重点：").append(nullToDash(p.focus())).append("\n");
                if (p.points() != null && !p.points().isEmpty()) {
                    sb.append("  涉及知识点：").append(String.join("、", p.points())).append("\n");
                }
            }
            if (plan.summary() != null && !plan.summary().isBlank()) {
                sb.append("\n小结：").append(plan.summary()).append("\n");
            }
        }
        return sb.toString();
    }

    // ---------- 内部工具方法 ----------

    private int normalizeLimit(Integer maxResults, int def, int max) {
        if (maxResults == null || maxResults <= 0) {
            return def;
        }
        return Math.min(maxResults, max);
    }

    private String tag(Integer flag, String label, String level) {
        if (flag == null || flag != 1) {
            return "";
        }
        // level 字段本身可能已包含该标签（如 level="985"），避免重复输出
        return (level != null && level.contains(label)) ? "" : " " + label;
    }

    private String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "未知" : s;
    }
}
