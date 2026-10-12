package com.aiweb.mapper;

import com.aiweb.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 对话历史的数据库操作接口。
 * 位置：src/main/java/com/aiweb/mapper/ChatMessageMapper.java
 * 作用：增删改查 chat_message 表。
 *
 * 继承 BaseMapper 后，insert / selectList 等常用方法 MyBatis-Plus 已经帮你写好了，
 * 这里不用写任何代码，也不用写 XML。
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
