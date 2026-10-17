package com.aiweb.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Agent 编排器的单元测试。
 * 位置：src/test/java/com/aiweb/agent/AgentOrchestratorTest.java
 *
 * 测什么（按价值排序）：
 *   1. 模型故障时走规则兜底（项目核心承诺：任何情况下有用、不报错）；
 *   2. 工具按 order 执行、shouldRun=false 记 skipped；
 *   3. 单个工具抛错不中断整条流程；
 *   4. 空目标直接引导。
 */
class AgentOrchestratorTest {

    /** 假工具：记录名字/顺序，可控制是否执行与产出 */
    static class FakeTool implements AgentTool {
        final String name;
        final int order;
        final boolean shouldRun;
        final RuntimeException toThrow;

        FakeTool(String name, int order, boolean shouldRun, RuntimeException toThrow) {
            this.name = name;
            this.order = order;
            this.shouldRun = shouldRun;
            this.toThrow = toThrow;
        }

        @Override public String name() { return name; }
        @Override public int order() { return order; }
        @Override public boolean shouldRun(AgentContext ctx) { return shouldRun; }

        @Override
        public ToolOutput execute(AgentContext ctx) {
            if (toThrow != null) throw toThrow;
            // 模拟真实工具的行为：把产出写回上下文（兜底模板从 ctx 读取组织答案）
            ctx.setCollegeNote(name + " 的产出");
            return ToolOutput.of(name + " 的产出");
        }
    }

    private AgentOrchestrator orchestratorWith(List<AgentTool> tools, ChatClient chatClient) {
        ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        return new AgentOrchestrator(tools, builder);
    }

    @Test
    @DisplayName("空目标：直接返回引导文案，不调用任何工具")
    void emptyGoalReturnsGuide() {
        ChatClient chatClient = Mockito.mock(ChatClient.class);
        AgentOrchestrator orchestrator = orchestratorWith(
                List.of(new FakeTool("理解需求", 1, true, null)), chatClient);

        AgentResult result = orchestrator.run("", "");
        assertTrue(result.answer().contains("请先描述你的目标"));
        assertFalse(result.degraded());
        assertTrue(result.steps().isEmpty(), "空目标不该产生任何步骤");
    }

    @Test
    @DisplayName("模型故障 → 降级 + 规则兜底答案（核心承诺）")
    void modelFailureFallsBackGracefully() {
        ChatClient chatClient = Mockito.mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(new RuntimeException("rate limited"));

        AgentOrchestrator orchestrator = orchestratorWith(List.of(
                new FakeTool("理解需求", 1, true, null),
                new FakeTool("检索院校专业", 2, true, null),
                new FakeTool("学情诊断", 4, false, null)), chatClient);

        AgentResult result = orchestrator.run("我想考山东大学", "");

        assertTrue(result.degraded(), "模型不可用应标记 degraded");
        assertNotNull(result.answer());
        assertFalse(result.answer().isBlank(), "兜底也必须有答案");
        // 工具产出应出现在兜底答案里（模板组织）
        assertTrue(result.answer().contains("检索院校专业")
                || result.answer().contains("院校专业"), "兜底答案应包含工具产出");

        // 步骤轨迹：shouldRun=false 的记 skipped
        AgentResult.Step diag = result.steps().stream()
                .filter(s -> s.name().equals("学情诊断")).findFirst().orElseThrow();
        assertEquals("skipped", diag.status());
        AgentResult.Step college = result.steps().stream()
                .filter(s -> s.name().equals("检索院校专业")).findFirst().orElseThrow();
        assertEquals("done", college.status());
    }

    @Test
    @DisplayName("模型可用 → 返回模型合成内容，不降级")
    void modelSuccessReturnsContent() {
        ChatClient chatClient = Mockito.mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = Mockito.mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.user(anyString())).thenReturn(spec);
        when(spec.call()).thenReturn(call);
        when(call.content()).thenReturn("这是模型合成的连贯建议");

        AgentOrchestrator orchestrator = orchestratorWith(
                List.of(new FakeTool("理解需求", 1, true, null)), chatClient);

        AgentResult result = orchestrator.run("帮我规划考研", "");
        assertEquals("这是模型合成的连贯建议", result.answer());
        assertFalse(result.degraded());
    }

    @Test
    @DisplayName("单个工具抛错不中断：该步记录出错，后续步骤照常")
    void toolFailureDoesNotBreakFlow() {
        ChatClient chatClient = Mockito.mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(new RuntimeException("模型挂了"));

        AgentOrchestrator orchestrator = orchestratorWith(List.of(
                new FakeTool("理解需求", 1, true, null),
                new FakeTool("检索院校专业", 2, true, new RuntimeException("数据库连接失败")),
                new FakeTool("学情诊断", 4, true, null)), chatClient);

        AgentResult result = orchestrator.run("帮我看看学情", "demo");

        AgentResult.Step broken = result.steps().stream()
                .filter(s -> s.name().equals("检索院校专业")).findFirst().orElseThrow();
        assertEquals("done", broken.status(), "出错步骤也应标记完成（有错误输出），不让流程中断");
        assertTrue(broken.output().contains("数据库连接失败"), "错误信息应记录在轨迹里");

        AgentResult.Step after = result.steps().stream()
                .filter(s -> s.name().equals("学情诊断")).findFirst().orElseThrow();
        assertEquals("done", after.status(), "后续步骤必须照常执行");
    }

    @Test
    @DisplayName("工具乱序传入也会按 order 排序执行")
    void toolsAreSortedByOrder() {
        ChatClient chatClient = Mockito.mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(new RuntimeException("模型不可用"));

        AgentOrchestrator orchestrator = orchestratorWith(List.of(
                new FakeTool("最后一步", 9, true, null),
                new FakeTool("第一步", 1, true, null),
                new FakeTool("中间步", 5, true, null)), chatClient);

        AgentResult result = orchestrator.run("测试顺序", "");
        List<String> names = result.steps().stream().map(AgentResult.Step::name).toList();
        assertEquals(List.of("第一步", "中间步", "最后一步"), names);
    }

    @Test
    @DisplayName("超长目标被截断到 500 字（保护提示词 token 预算）")
    void overLongGoalIsTruncated() {
        ChatClient chatClient = Mockito.mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(new RuntimeException("模型不可用"));

        AgentOrchestrator orchestrator = orchestratorWith(
                List.of(new FakeTool("理解需求", 1, true, null)), chatClient);

        AgentResult result = orchestrator.run("考".repeat(2000), "");
        assertEquals(501, result.goal().length(), "500 字 + 1 个省略号");
        assertTrue(result.goal().endsWith("…"));
    }
}
