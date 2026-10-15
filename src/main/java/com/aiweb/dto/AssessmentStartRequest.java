package com.aiweb.dto;

/**
 * 开始测评的请求。
 * 位置：src/main/java/com/aiweb/dto/AssessmentStartRequest.java
 *
 * 字段说明：
 *   studentId  —— 学生标识，前端用 localStorage 生成并保存（项目无登录）
 *   subject    —— 科目代码：math / english / politics（不传默认 math，兼容旧前端）
 *   targetMath —— 目标数学类型：math1 / math2 / math3 / all（仅数学科目有效，用于筛选题目）
 *   count      —— 抽取多少道题（不传默认 10）
 */
public record AssessmentStartRequest(String studentId, String subject, String targetMath, Integer count) {
}
