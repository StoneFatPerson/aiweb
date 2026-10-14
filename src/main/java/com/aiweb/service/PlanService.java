package com.aiweb.service;

import com.aiweb.dto.AssessmentResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 备考计划生成（规则版，不依赖大模型，稳定可演示）。
 * 位置：src/main/java/com/aiweb/service/PlanService.java
 *
 * 作用：拿到诊断结果（各知识点掌握度）后，按「基础 → 强化 → 冲刺」三阶段
 *       生成可执行计划。后续可接大模型做语言润色，但核心逻辑先保持规则化，
 *       避免免费档大模型限流影响演示。
 */
@Service
public class PlanService {

    /**
     * 根据诊断结果生成三阶段计划。
     * 规则：
     *   基础阶段 —— 按章节顺序系统过全部知识点（打底子）
     *   强化阶段 —— 集中攻克「薄弱」知识点（弱项优先）
     *   冲刺阶段 —— 全真模拟 + 查漏补缺
     */
    public AssessmentResult.StudyPlan buildPlan(List<AssessmentResult.PointDiagnosis> diagnosis) {
        List<String> allNames = diagnosis.stream().map(AssessmentResult.PointDiagnosis::pointName).toList();
        List<String> weak = diagnosis.stream()
                .filter(d -> d.mastery() < 0.4)
                .map(AssessmentResult.PointDiagnosis::pointName)
                .toList();
        List<String> strong = diagnosis.stream()
                .filter(d -> d.mastery() >= 0.7)
                .map(AssessmentResult.PointDiagnosis::pointName)
                .toList();

        AssessmentResult.PhasePlan base = new AssessmentResult.PhasePlan(
                "基础阶段", "第 1-6 周",
                "按章节顺序系统过一遍全部知识点，搞清公式来源与基本题型，不留盲区。",
                allNames);

        AssessmentResult.PhasePlan reinforce = new AssessmentResult.PhasePlan(
                "强化阶段", "第 7-10 周",
                weak.isEmpty() ? "当前无明显薄弱点，做综合题与中高难度题保持手感。"
                               : "集中攻克薄弱知识点，配合专项训练与错题复盘。",
                weak.isEmpty() ? List.of("巩固已掌握内容，做综合题") : weak);

        AssessmentResult.PhasePlan sprint = new AssessmentResult.PhasePlan(
                "冲刺阶段", "第 11-12 周",
                "全真模拟找节奏，弱项二次强化，查漏补缺。",
                allNames);

        String summary = weak.isEmpty()
                ? "诊断显示你整体掌握良好，建议保持节奏、多做综合题与真题模拟。"
                : "你有 " + weak.size() + " 个薄弱知识点，建议把主要精力放在强化阶段，优先攻克："
                    + String.join("、", weak) + "。已扎实的有 " + strong.size() + " 个，可少量保温。";

        return new AssessmentResult.StudyPlan(List.of(base, reinforce, sprint), summary);
    }
}
