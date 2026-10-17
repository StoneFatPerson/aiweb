package com.aiweb.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BKT 贝叶斯知识追踪算法的单元测试。
 * 位置：src/test/java/com/aiweb/service/DiagnosisServiceTest.java
 *
 * 测什么：算法是「学情诊断」的地基，必须是纯函数、可复现、数学性质正确。
 * 这些测试不依赖 Spring 容器、不依赖数据库、不依赖大模型——纯逻辑验证。
 */
class DiagnosisServiceTest {

    private final DiagnosisService service = new DiagnosisService();

    @Test
    @DisplayName("答对后掌握概率上升，答错后下降")
    void updateDirectionIsCorrect() {
        double afterCorrect = service.update(0.5, true);
        double afterWrong = service.update(0.5, false);
        assertTrue(afterCorrect > 0.5, "答对后应该上升");
        assertTrue(afterWrong < 0.5, "答错后应该下降");
    }

    @Test
    @DisplayName("先验值就是 0.3")
    void priorIs03() {
        assertEquals(0.3, service.getPrior());
    }

    @Test
    @DisplayName("边界性质：0 与 1 都是吸收态；接近边界时「意外」生效")
    void boundariesStayValid() {
        // 吸收态：分母第二项 (1-prior)*x 为 0，结果恒等于边界值本身（标准 BKT 性质）
        assertEquals(1.0, service.update(1.0, true), 1e-9);
        assertEquals(1.0, service.update(1.0, false), 1e-9);
        assertEquals(0.0, service.update(0.0, false), 1e-9);
        assertEquals(0.0, service.update(0.0, true), 1e-9);
        // 接近边界时两种「意外」都被建模：已掌握答错会掉、未掌握答对会升
        assertTrue(service.update(0.99, false) < 0.99, "高掌握度答错应下降");
        assertTrue(service.update(0.01, true) > 0.01, "低掌握度答对应上升");
        // 实际系统用 0.3 先验（远离吸收态），保证观测能持续更新
    }

    @Test
    @DisplayName("连续答对收敛到高位，连续答错收敛到低位")
    void convergesWithEvidence() {
        double p = service.getPrior();
        for (int i = 0; i < 25; i++) p = service.update(p, true);
        assertTrue(p > 0.99, "连续 25 次答对应收敛到接近 1，实际 " + p);

        p = service.getPrior();
        for (int i = 0; i < 25; i++) p = service.update(p, false);
        assertTrue(p < 0.01, "连续 25 次答错应收敛到接近 0，实际 " + p);
    }

    @Test
    @DisplayName("单次意外不会一步登天：低先验蒙对一次不会超过 0.5")
    void singleGuessIsBounded() {
        double p = service.update(0.01, true); // 从「几乎没会」蒙对一次
        assertTrue(p < 0.5, "单次蒙对不应跳到 0.5 以上（猜测率 0.2 的限制），实际 " + p);
        // 但确实应该上升
        assertTrue(p > 0.01);
    }

    @Test
    @DisplayName("掌握度越高，答错的「伤害」越大（先验影响更新幅度）")
    void updateMagnitudeDependsOnPrior() {
        double highDrop = 0.9 - service.update(0.9, false);
        double lowDrop = 0.2 - service.update(0.2, false);
        assertTrue(highDrop > lowDrop, "高掌握度答错一次跌得更多");
    }

    @Test
    @DisplayName("输出永远是合法概率 [0,1]")
    void outputAlwaysValidProbability() {
        for (double prior = 0.0; prior <= 1.0001; prior += 0.1) {
            for (boolean correct : new boolean[]{true, false}) {
                double p = service.update(prior, correct);
                assertTrue(p >= 0.0 && p <= 1.0,
                        "prior=" + prior + " correct=" + correct + " 产出 " + p + " 越界");
            }
        }
    }
}
