package com.aiweb.mapper;

import com.aiweb.entity.Mastery;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 掌握度表操作。
 * 位置：src/main/java/com/aiweb/mapper/MasteryMapper.java
 * 说明：主键是 (student_id, point_code) 组合，没有单独自增 id，
 *      更新时用 LambdaUpdateWrapper 按两个字段定位，避免依赖单 id。
 */
public interface MasteryMapper extends BaseMapper<Mastery> {
}
