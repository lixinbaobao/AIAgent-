package com.zhangbo.yuaiagent.session;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天会话信息
 */
@Data
public class ChatSession {

    /**
     * 会话ID
     */
    private String id;

    /**
     * 会话标题（从第一条用户消息提取）
     */
    private String title;

    /**
     * 消息历史（JSON格式）
     */
    private List<SessionMessage> messages = new ArrayList<>();

    /**
     * 创建时间
     */
    private Long createTime;

    /**
     * 更新时间
     */
    private Long updateTime;
}