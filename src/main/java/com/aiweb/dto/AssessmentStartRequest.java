package com.aiweb.dto;

/**
 * 开始测评的请求。
 * 位置：src/main/java/com/aiweb/dto/AssessmentStartRequest.java
 *
 * 字段说明：
 *   studentId  —— 学生标识，前端用 localStorage 生成并保存（项目无登录）
 *   targetMath —— 目标数学类型：math1 / math2 / math3 / all（用于筛选题目）
 *   count      —— 抽取多少道题（不传默认 10）
 */
public record AssessmentStartRequest(String studentId, String targetMath, Integer count) {
}
