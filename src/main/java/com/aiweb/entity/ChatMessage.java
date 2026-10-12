package com.aiweb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话历史表对应的 Java 类。
 * 位置：src/main/java/com/aiweb/entity/ChatMessage.java
 * 作用：一条聊天记录在程序里的样子。类名对应数据库表名 chat_message。
 *
 * @Data 是 Lombok 提供的注解，自动生成 get/set/toString，不用手写几百行。
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID：同一个人一次聊天的编号，用来区分不同对话 */
    private String sessionId;

    /** 角色：user 表示用户说的，assistant 表示 AI 说的 */
    private String role;

    /** 消息内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;
}
