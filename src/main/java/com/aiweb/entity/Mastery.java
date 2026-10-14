package com.aiweb.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 某个学生对某个知识点的掌握度（BKT 追踪）。
 * 位置：src/main/java/com/aiweb/entity/Mastery.java
 * 作用：对应 mastery 表。主键是 (student_id, point_code) 组合，没有单独自增 id。
 *      每次作答后由 DiagnosisService 用 BKT 公式更新 mastery_prob。
 */
@Data
@TableName("mastery")
public class Mastery {

    /** 学生标识（前端 localStorage 生成，无登录） */
    private String studentId;

    /** 知识点代码 */
    private String pointCode;

    /** BKT 估计的掌握概率（0-1） */
    private BigDecimal masteryProb;

    /** 作答次数 */
    private Integer attempts;

    /** 最近更新时间 */
    private LocalDateTime lastUpdated;
}
