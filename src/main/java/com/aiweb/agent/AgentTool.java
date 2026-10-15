package com.aiweb.agent;

import java.util.List;

/**
 * Agent 工具接口：编排流程中的一个可复用「步骤」。
 * 位置：src/main/java/com/aiweb/agent/AgentTool.java
 * 作用：每个工具只做一件事（理解需求 / 查院校 / 查科普 / 诊断 / 出计划），
 *       编排器按 order() 顺序调用，并把每步结果透明地展示给用户——
 *       这正是「AI 是怎么一步步得出结论的」可见可解释的关键。
 */
public interface AgentTool {

    /** 步骤显示名，如「检索院校专业」 */
    String name();

    /** 执行顺序（越小越先；IntentTool=1 必须最先跑，因为它决定后续跑哪些工具） */
    default int order() {
        return 100;
    }

    /** 是否执行（编排器依据上一步的意图判断） */
    default boolean shouldRun(AgentContext ctx) {
        return true;
    }

    /** 执行并返回该步产出：summary 供展示与汇总，sources 供引用 */
    ToolOutput execute(AgentContext ctx);

    /** 单步产出 */
    record ToolOutput(String summary, List<String> sources) {
        public static ToolOutput of(String summary) {
            return new ToolOutput(summary, List.of());
        }

        public static ToolOutput of(String summary, List<String> sources) {
            return new ToolOutput(summary, sources == null ? List.of() : sources);
        }
    }
}
