package com.aiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 考研数学知识点（用于诊断时按知识点追踪掌握度）。
 * 位置：src/main/java/com/aiweb/entity/KnowledgePoint.java
 * 作用：对应 knowledge_point 表。数据由 math JSON 的 chapters/points 导入。
 */
@Data
@TableName("knowledge_point")
public class KnowledgePoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识点代码，如 MATH-01-01（唯一） */
    private String code;

    /** 知识点名称 */
    private String name;

    /** 所属章节代码 */
    private String chapterCode;

    /** 所属章节名称 */
    private String chapterName;

    /** 适用数学类型 */
    private String applicableTo;
}
