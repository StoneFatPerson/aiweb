package com.aiweb.agent;

import com.aiweb.service.RagService;
import org.springframework.stereotype.Component;

/**
 * 步骤3：科普知识检索（RAG）。用自建考研知识库为本次目标提供可靠背景知识。
 * 位置：src/main/java/com/aiweb/agent/ScienceTool.java
 * 复用 RagService.ask：检索 + 阈值兜底防幻觉 + 限流优雅降级，答案可溯源。
 */
@Component
public class ScienceTool implements AgentTool {

    private final RagService ragService;

    public ScienceTool(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public String name() {
        return "科普知识检索";
    }

    @Override
    public int order() {
        return 3;
    }

    @Override
    public boolean shouldRun(AgentContext ctx) {
        return ctx.isRunScience();
    }

    @Override
    public ToolOutput execute(AgentContext ctx) {
        RagService.RagAnswer ans = ragService.ask(ctx.getGoal());
        ctx.setScienceAnswer(ans.answer);
        ctx.getScienceSources().addAll(ans.sources);
        return ToolOutput.of(ans.answer, ans.sources);
    }
}
