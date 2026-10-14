package com.aiweb.service;

import org.springframework.stereotype.Service;

/**
 * BKT 贝叶斯知识追踪（Bayesian Knowledge Tracing）诊断算法。
 * 位置：src/main/java/com/aiweb/service/DiagnosisService.java
 *
 * 作用：根据「学生某题答对/答错」，估算他对该知识点「到底掌握了几成」。
 * 这是本项目 AI 技术深度里的「算法亮点」，技术文档要重点写。
 *
 * 通俗解释：
 *   每个知识点都有一个「掌握概率」P(L)。每做一次相关题，就根据对错更新它：
 *   - 答对了 → 更可能已掌握
 *   - 答错了 → 更可能没掌握
 *   但还要考虑两种「意外」：已经会了却手滑答错（slip），没会却蒙对了（guess）。
 *   算法把这两种意外也量化进去，所以比「单纯看正确率」更准。
 */
@Service
public class DiagnosisService {

    /** 先验：第一次接触某知识点时，假设掌握概率为 0.3（偏向「还没掌握」） */
    public static final double PRIOR = 0.3;

    /** 失误率 slip：已掌握却答错的概率（比如粗心） */
    private static final double SLIP = 0.1;

    /** 猜测率 guess：未掌握却蒙对的概率（比如四选一瞎猜） */
    private static final double GUESS = 0.2;

    /** 取先验掌握概率 */
    public double getPrior() {
        return PRIOR;
    }

    /**
     * 单次观测更新：给「旧掌握概率 prior」和一个「对错观测 correct」，返回「新掌握概率」。
     * 公式（标准 BKT）：
     *   分子 = prior * P(观测 | 已掌握)
     *   分母 = 分子 + (1-prior) * P(观测 | 未掌握)
     */
    public double update(double prior, boolean correct) {
        double pObsGivenL = correct ? (1 - SLIP) : SLIP;          // 已掌握时出现该观测的概率
        double pObsGivenNotL = correct ? GUESS : (1 - GUESS);    // 未掌握时出现该观测的概率
        double numerator = prior * pObsGivenL;
        double denominator = numerator + (1 - prior) * pObsGivenNotL;
        return denominator > 0 ? numerator / denominator : prior;
    }
}
