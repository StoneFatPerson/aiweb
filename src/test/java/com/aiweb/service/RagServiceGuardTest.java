package com.aiweb.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG 输入防护（截断）的单元测试。
 * 位置：src/test/java/com/aiweb/service/RagServiceGuardTest.java
 *
 * 测什么：超长输入必须被截断（保护检索质量与 token 预算），
 * 短输入原样返回，null 安全。truncate 提为包级静态（为可测性的小重构）。
 */
class RagServiceGuardTest {

    @Test
    @DisplayName("短于上限的输入原样返回")
    void shortInputUnchanged() {
        assertEquals("学硕和专硕有什么区别", RagService.truncate("学硕和专硕有什么区别", 500));
        assertEquals("正好五百", RagService.truncate("正好五百", 4));
    }

    @Test
    @DisplayName("超长输入截断到上限 + 省略号")
    void longInputTruncated() {
        String result = RagService.truncate("x".repeat(1000), 500);
        assertEquals(501, result.length(), "500 字 + 1 个省略号");
        assertTrue(result.endsWith("…"));
        assertTrue(result.startsWith("x".repeat(500)));
    }

    @Test
    @DisplayName("null 安全")
    void nullSafe() {
        assertEquals("", RagService.truncate(null, 500));
    }

    @Test
    @DisplayName("上限为 0 的边界")
    void zeroMax() {
        assertEquals("…", RagService.truncate("abc", 0));
        assertEquals("", RagService.truncate("", 0));
    }
}
