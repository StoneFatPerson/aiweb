package com.aiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

/**
 * 院校信息。
 * 位置：src/main/java/com/aiweb/entity/College.java
 * 作用：对应数据库 college 表，存放院校基本信息（不含考公内容）。
 */
@Data
@TableName("college")
public class College {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 研招网院校代码 */
    private String code;

    /** 院校全称 */
    private String name;

    /** 简称，如「山大」 */
    private String shortName;

    /** 所在城市 */
    private String city;

    /** 层次：985 / 211 / 省属重点 / 省属 */
    private String level;

    // 注意：属性名 is985 里的 985 是数字，MyBatis-Plus 的驼峰转下划线
    // 识别不出来（只认大写字母），必须手动指定列名，否则会报 Unknown column 'is985'
    @TableField("is_985")
    private Integer is985;

    @TableField("is_211")
    private Integer is211;

    private Integer isDoubleFirst;

    /** 是否自主划线（全国 34 所） */
    private Integer isSelfLine;

    private String website;

    /** 是否已与官方核对：0 未核对，1 已核对 */
    private Integer verified;

    /** 该院校的热门专业列表。数据库里没有这个字段，查询时手动填充 */
    @TableField(exist = false)
    private List<Major> majors;
}
