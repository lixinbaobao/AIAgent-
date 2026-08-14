package com.zhangbo.yuaiagent.session;

import lombok.Data;

/**
 * 会话消息记录
 */
@Data
public class SessionMessage {

    /**
     * 角色：user / assistant
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息时间
     */
    private Long time;

    public SessionMessage() {
    }

    public SessionMessage(String role, String content, Long time) {
        this.role = role;
        this.content = content;
        this.time = time;
    }
}