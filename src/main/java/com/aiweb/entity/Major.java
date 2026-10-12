package com.aiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 考研专业信息。
 * 位置：src/main/java/com/aiweb/entity/Major.java
 * 作用：对应数据库 major 表，只收录考研专业，每所院校仅收录最热门的几个。
 */
@Data
@TableName("major")
public class Major {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属院校 ID */
    private Long collegeId;

    /** 专业代码 */
    private String code;

    /** 专业名称 */
    private String name;

    /** 学位类型：学硕 / 专硕 */
    private String degreeType;

    /** 学科门类：工学 / 医学 / 管理学 等 */
    private String category;

    /** 一级学科 */
    private String discipline;

    /** 所属学院 */
    private String department;

    /** 公共课组合，如 政治+英二+数二 */
    private String publicCourses;

    /** 学制（年） */
    private Integer duration;

    private Integer isPopular;

    private Integer verified;

    /** 院校名称，非数据库字段，联表查询时填充 */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String collegeName;
}
