package com.aiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条做题历史记录（对应 answer_history 表）。
 * 位置：src/main/java/com/aiweb/entity/AnswerHistory.java
 * 作用：每次提交测评时，把每道题的「用户作答 + 标准答案 + 对错」落库，
 *      供「历史做题记录」展示与「清除历史数据」使用。
 */
@Data
@TableName("answer_history")
public class AnswerHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学生标识（前端 localStorage 生成，无登录） */
    private String studentId;

    /** 题目编号 */
    private String questionCode;

    /** 用户作答（已归一化，便于阅读） */
    private String userAnswer;

    /** 该题标准答案 */
    private String correctAnswer;

    /** 是否正确：0 否 / 1 是 */
    private Integer isCorrect;

    /** 提交时间 */
    private LocalDateTime createdAt;
}
