package com.aiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 题库中的一道题。
 * 位置：src/main/java/com/aiweb/entity/Question.java
 * 作用：对应 question_bank 表。题目由 data/math-seed-batch1.json 在启动时自动导入。
 */
@Data
@TableName("question_bank")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 题目编号，如 MATH-Q0001（唯一） */
    private String code;

    /** 学科代码，如 math */
    private String subjectCode;

    /** 章节代码，如 MATH-01 */
    private String chapterCode;

    /** 知识点代码（逗号分隔，可多个），用于诊断追踪 */
    private String pointCodes;

    /** 适用数学类型：math1/math2/math3（逗号分隔） */
    private String applicableTo;

    /** 题型：SINGLE_CHOICE / FILL_BLANK */
    private String type;

    /** 难度 1-3 */
    private Integer difficulty;

    /** 年份，种子题多为 null */
    private String year;

    /** 来源：ORIGINAL_SEED（种子题）/ AI_GENERATED（AI 生成题） */
    private String source;

    /** 题干 */
    private String stem;

    /** 选择题选项，JSON 数组 [{key, content}]；填空题为 null */
    private String options;

    /** 正确答案（选择题为选项 key 如 C；填空题为文本如 1/2） */
    private String answer;

    /** 解析 */
    private String explanation;

    /** 关键词（逗号分隔） */
    private String keywords;

    /** 是否已核对：0 未核对，1 已核对（种子题为原创，已核对） */
    private Integer verified;
}
