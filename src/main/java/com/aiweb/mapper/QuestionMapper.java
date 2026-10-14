package com.aiweb.mapper;

import com.aiweb.entity.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 题库表操作。
 * 位置：src/main/java/com/aiweb/mapper/QuestionMapper.java
 * 作用：增删改查 question_bank 表，继承 BaseMapper 后常用方法已内置。
 */
public interface QuestionMapper extends BaseMapper<Question> {
}
