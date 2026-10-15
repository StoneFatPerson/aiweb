package com.aiweb.agent;

import com.aiweb.entity.College;
import com.aiweb.entity.Major;
import com.aiweb.service.CollegeService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 步骤2：检索院校专业。把意图匹配到的院校/专业整理成简明结论。
 * 位置：src/main/java/com/aiweb/agent/CollegeSearchTool.java
 *
 * 设计说明：院校、专业这类事实型数据全部来自数据库（不经过大模型），
 * 避免模型自由编造院校层次、专业设置等信息。
 * 两种触发路径：
 *  1）目标中提到具体院校/专业名（IntentTool 已匹配好）；
 *  2）目标是列举式的（「山东有哪些 985」「青岛的大学」）——IntentTool 解析出
 *     城市 / 985 / 211 筛选条件，本工具按条件查库出列表。
 */
@Component
public class CollegeSearchTool implements AgentTool {

    private static final int MAX_SHOW = 8;

    private final CollegeService collegeService;

    public CollegeSearchTool(CollegeService collegeService) {
        this.collegeService = collegeService;
    }

    @Override
    public String name() {
        return "检索院校专业";
    }

    @Override
    public int order() {
        return 2;
    }

    @Override
    public boolean shouldRun(AgentContext ctx) {
        return ctx.isRunCollege();
    }

    @Override
    public ToolOutput execute(AgentContext ctx) {
        List<College> colleges = ctx.getColleges();
        List<Major> majors = ctx.getMajors();

        // 没有具体匹配时，若解析出了筛选条件（如「山东有哪些 985」），按条件查库
        if (colleges.isEmpty() && majors.isEmpty() && ctx.hasCollegeFilter()) {
            List<College> filtered = collegeService.listColleges(
                    ctx.getFilterCity().isBlank() ? null : ctx.getFilterCity(),
                    null, ctx.getFilter985(), ctx.getFilter211(), null, null);
            colleges = filtered;
            ctx.getColleges().addAll(filtered); // 写回上下文，供汇总与来源展示
        }

        if (colleges.isEmpty() && majors.isEmpty()) {
            String note = "未在库中匹配到具体院校或专业名称；可到「院校库」页按城市 / 层次 / 985 / 211 筛选查看库内山东院校。";
            ctx.setCollegeNote(note);
            return ToolOutput.of(note);
        }

        StringBuilder sb = new StringBuilder();
        if (!colleges.isEmpty()) {
            sb.append("筛选到 ").append(colleges.size()).append(" 所院校：")
              .append(colleges.stream().limit(MAX_SHOW)
                      .map(c -> c.getName() + "（" + nz(c.getCity()) + "・" + levelOf(c) + "）")
                      .collect(Collectors.joining("、")));
            if (colleges.size() > MAX_SHOW) sb.append(" 等");
            sb.append("。");
        }
        if (!majors.isEmpty()) {
            List<String> majorNames = majors.stream().map(Major::getName).distinct().toList();
            sb.append("相关专业 ").append(majorNames.size()).append(" 个：")
              .append(majorNames.stream().limit(MAX_SHOW).collect(Collectors.joining("、")));
            if (majorNames.size() > MAX_SHOW) sb.append(" 等");
            sb.append("。具体招生信息以院校研究生院官网为准。");
        }
        String summary = sb.toString();
        ctx.setCollegeNote(summary);
        return ToolOutput.of(summary);
    }

    private String levelOf(College c) {
        StringBuilder sb = new StringBuilder();
        if (c.getIs985() != null && c.getIs985() == 1) sb.append("985");
        if (c.getIs211() != null && c.getIs211() == 1) sb.append(sb.length() > 0 ? "/211" : "211");
        if (sb.length() == 0) sb.append(nz(c.getLevel()));
        return sb.toString();
    }

    private String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
