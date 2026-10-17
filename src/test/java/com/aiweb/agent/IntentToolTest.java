package com.aiweb.agent;

import com.aiweb.entity.College;
import com.aiweb.entity.Major;
import com.aiweb.service.CollegeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Agent「理解需求」工具的单元测试。
 * 位置：src/test/java/com/aiweb/agent/IntentToolTest.java
 *
 * 测什么：意图判断是全流程的地基（它决定后续跑哪些工具），
 * 必须稳定、可复现、不依赖大模型。这里用 Mockito 假造院校库数据。
 */
class IntentToolTest {

    private CollegeService collegeService;
    private IntentTool tool;

    @BeforeEach
    void setUp() {
        collegeService = Mockito.mock(CollegeService.class);

        College sdu = new College();
        sdu.setName("山东大学");
        sdu.setShortName("山大");
        College ouc = new College();
        ouc.setName("中国海洋大学");
        ouc.setShortName("海大");
        when(collegeService.listColleges(isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(sdu, ouc));

        Major cs = new Major();
        cs.setName("计算机科学与技术");
        Major finance = new Major();
        finance.setName("金融学");
        when(collegeService.listMajors(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(cs, finance));

        tool = new IntentTool(collegeService);
    }

    private AgentContext run(String goal) {
        AgentContext ctx = new AgentContext(goal, "");
        tool.execute(ctx);
        return ctx;
    }

    @Test
    @DisplayName("提到库内院校全称/简称 → 命中并触发院校检索")
    void matchesCollegeByName() {
        AgentContext byFull = run("我想考山东大学");
        assertEquals(1, byFull.getColleges().size());
        assertEquals("山东大学", byFull.getColleges().get(0).getName());
        assertTrue(byFull.isRunCollege());

        AgentContext byShort = run("山大怎么样");
        assertEquals(1, byShort.getColleges().size());
        assertTrue(byShort.isRunCollege());
    }

    @Test
    @DisplayName("学科关键词互含：「计算机」命中「计算机科学与技术」")
    void matchesMajorByKeyword() {
        AgentContext ctx = run("计算机专业该怎么准备");
        assertFalse(ctx.getMajors().isEmpty(), "「计算机」应通过关键词互含匹配到库内专业");
        assertEquals("计算机科学与技术", ctx.getMajors().get(0).getName());
        assertTrue(ctx.isRunCollege());
    }

    @Test
    @DisplayName("列举式筛选：「山东有哪些 985」→ 解析出 985 筛选条件并触发检索")
    void parsesListStyleFilters() {
        AgentContext ctx = run("山东有哪些985院校？");
        assertTrue(ctx.hasCollegeFilter(), "应解析到筛选条件");
        assertEquals(1, ctx.getFilter985());
        assertTrue(ctx.isRunCollege(), "有筛选词也要触发院校检索");
    }

    @Test
    @DisplayName("城市 + 层次组合筛选：「青岛有哪些 211 大学」")
    void parsesCityAndLevel() {
        AgentContext ctx = run("青岛有哪些211大学");
        assertEquals("青岛", ctx.getFilterCity());
        assertEquals(1, ctx.getFilter211());
        assertTrue(ctx.isRunCollege());
    }

    @Test
    @DisplayName("纯科普问题 → 不跑院校，跑科普")
    void scienceOnlyQuestion() {
        AgentContext ctx = run("学硕和专硕有什么区别？");
        assertFalse(ctx.isRunCollege(), "纯名词解释不该触发院校检索");
        assertTrue(ctx.isRunScience());
    }

    @Test
    @DisplayName("科普 + 院校混合目标 → 两步都跑")
    void mixedGoalRunsBoth() {
        AgentContext ctx = run("山东大学的复试流程是怎样的？");
        assertTrue(ctx.isRunCollege(), "提到院校 → 院校检索");
        assertTrue(ctx.isRunScience(), "提到复试 → 科普检索");
    }

    @Test
    @DisplayName("空目标不崩，且至少跑科普（给出通用帮助）")
    void emptyGoalIsSafe() {
        AgentContext ctx = run("");
        assertFalse(ctx.isRunCollege());
        assertTrue(ctx.isRunScience(), "空目标应兜底跑科普，而不是什么都不做");
        assertNotNull(ctx.getIntentSummary());
    }

    @Test
    @DisplayName("填了学号才触发诊断")
    void diagnosisOnlyWithStudentId() {
        AgentContext withId = new AgentContext("帮我规划", "demo");
        tool.execute(withId);
        assertTrue(withId.isRunDiagnosis());

        AgentContext withoutId = run("帮我规划");
        assertFalse(withoutId.isRunDiagnosis());
    }
}
