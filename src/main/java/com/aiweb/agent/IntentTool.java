package com.aiweb.agent;

import com.aiweb.entity.College;
import com.aiweb.entity.Major;
import com.aiweb.service.CollegeService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 步骤1：理解需求。分析用户目标，决定后续要调用哪些工具。
 * 位置：src/main/java/com/aiweb/agent/IntentTool.java
 *
 * 说明：用「数据驱动 + 关键词」判断，不依赖大模型——稳定、可复现。
 * 把用户目标和库内院校名/简称、专业名做包含匹配，命中就走院校检索。
 */
@Component
public class IntentTool implements AgentTool {

    // 触发「院校检索」的词
    private static final List<String> COLLEGE_HINTS = List.of(
            "大学", "学院", "院校", "学校", "选校", "择校", "专业", "考哪", "报哪", "目标院校", "高校");
    // 触发「科普检索」的词
    private static final List<String> SCIENCE_HINTS = List.of(
            "时间线", "流程", "科目", "学硕", "专硕", "国家线", "院校线", "自划线",
            "复试", "调剂", "报名", "确认", "推免", "免试", "全日制", "非全", "报录比",
            "联考", "统考", "是什么", "区别", "多久", "什么时候", "几月", "建议", "怎么",
            "如何", "复习", "备考", "规划", "计划", "基础", "强化", "冲刺", "A区", "B区");
    // 常见学科关键词：用户爱说「计算机」「金融」，而库内专业全名是「计算机科学与技术」「金融学」，
    // 所以除了「目标包含专业全名」，再做一层「关键词互含」匹配，提升命中率。
    private static final List<String> MAJOR_KEYWORDS = List.of(
            "计算机", "软件", "网络", "信息安全", "人工智能", "数据", "电子", "通信", "自动化",
            "电气", "机械", "土木", "材料", "化学", "化工", "生物", "数学", "物理", "统计",
            "金融", "经济", "会计", "工商", "管理", "法学", "法律", "教育", "心理", "新闻",
            "传播", "英语", "日语", "汉语言", "文学", "历史", "哲学", "医学", "临床", "护理",
            "药学", "建筑", "规划", "环境", "食品", "农学", "林学", "矿业", "石油", "地质",
            "纺织", "交通运输", "船舶", "航空", "航天", "兵器", "翻译", "设计", "艺术",
            "音乐", "体育", "马克思主义");

    private final CollegeService collegeService;

    public IntentTool(CollegeService collegeService) {
        this.collegeService = collegeService;
    }

    @Override
    public String name() {
        return "理解需求";
    }

    @Override
    public int order() {
        return 1;
    }

    @Override
    public ToolOutput execute(AgentContext ctx) {
        String goal = ctx.getGoal();

        // 数据驱动匹配：用户目标里出现了库内院校名/简称 → 记为匹配院校
        List<College> allColleges = collegeService.listColleges(null, null, null, null, null, null);
        for (College c : allColleges) {
            if (contains(goal, c.getName()) || contains(goal, c.getShortName())) {
                ctx.getColleges().add(c);
            }
        }
        // 用户目标里出现了库内专业名 → 记为匹配专业
        List<Major> allMajors = collegeService.listMajors(null, null, null, null);
        for (Major m : allMajors) {
            if (contains(goal, m.getName()) || majorKeywordHit(goal, m.getName())) {
                ctx.getMajors().add(m);
            }
        }

        boolean collegeByData = !ctx.getColleges().isEmpty() || !ctx.getMajors().isEmpty();
        ctx.setRunCollege(collegeByData || anyHit(goal, COLLEGE_HINTS));
        ctx.setRunScience(anyHit(goal, SCIENCE_HINTS) || !ctx.isRunCollege());
        ctx.setRunDiagnosis(ctx.getStudentId() != null && !ctx.getStudentId().isBlank());

        StringBuilder sb = new StringBuilder("目标：").append(goal.isEmpty() ? "(未填写)" : goal);
        if (ctx.isRunDiagnosis()) {
            sb.append("；已提供学号 ").append(ctx.getStudentId()).append("，将结合个人学情");
        }
        sb.append("。计划执行：");
        if (ctx.isRunCollege()) sb.append("院校专业检索 ");
        if (ctx.isRunScience()) sb.append("科普知识检索 ");
        if (ctx.isRunDiagnosis()) sb.append("学情诊断与计划");
        String summary = sb.toString().trim();
        ctx.setIntentSummary(summary);
        return ToolOutput.of(summary);
    }

    private boolean contains(String goal, String name) {
        return name != null && !name.isBlank() && goal.contains(name.trim());
    }

    /** 学科关键词互含：目标里出现「计算机」且专业全名里也有「计算机」即算匹配 */
    private boolean majorKeywordHit(String goal, String majorName) {
        if (majorName == null) return false;
        for (String kw : MAJOR_KEYWORDS) {
            if (goal.contains(kw) && majorName.contains(kw)) return true;
        }
        return false;
    }

    private boolean anyHit(String goal, List<String> words) {
        for (String w : words) {
            if (goal.contains(w)) return true;
        }
        return false;
    }
}
