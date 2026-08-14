package com.zhangbo.yuaiagent.session;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话管理器
 * 负责会话的保存、加载和列表
 */
@Component
@Slf4j
public class SessionManager {

    @Value("${app.session.save-dir:./sessions}")
    private String sessionSaveDir;

    @PostConstruct
    public void init() {
        // 确保会话保存目录存在
        FileUtil.mkdir(sessionSaveDir);
        log.info("Session save directory: {}", sessionSaveDir);
    }

    /**
     * 保存会话
     */
    public void saveSession(ChatSession session) {
        try {
            String filePath = getSessionFilePath(session.getId());
            session.setUpdateTime(System.currentTimeMillis());
            String json = JSONUtil.toJsonPrettyStr(session);
            FileUtil.writeUtf8String(json, filePath);
            log.info("Saved session: {}", session.getId());
        } catch (Exception e) {
            log.error("Failed to save session: {}", session.getId(), e);
        }
    }

    /**
     * 加载会话
     */
    public ChatSession loadSession(String sessionId) {
        try {
            String filePath = getSessionFilePath(sessionId);
            if (!FileUtil.exist(filePath)) {
                return null;
            }
            String json = FileUtil.readUtf8String(filePath);
            return JSONUtil.toBean(json, ChatSession.class);
        } catch (Exception e) {
            log.error("Failed to load session: {}", sessionId, e);
            return null;
        }
    }

    /**
     * 获取所有会话列表（按更新时间倒序）
     */
    public List<ChatSession> listSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        try {
            File dir = new File(sessionSaveDir);
            if (!dir.exists() || !dir.isDirectory()) {
                return sessions;
            }

            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null) {
                return sessions;
            }

            for (File file : files) {
                try {
                    String json = FileUtil.readUtf8String(file);
                    ChatSession session = JSONUtil.toBean(json, ChatSession.class);
                    sessions.add(session);
                } catch (Exception e) {
                    log.warn("Failed to parse session file: {}", file.getName());
                }
            }

            // 按更新时间倒序排列
            sessions.sort(Comparator.comparingLong(ChatSession::getUpdateTime).reversed());
        } catch (Exception e) {
            log.error("Failed to list sessions", e);
        }
        return sessions;
    }

    /**
     * 删除会话
     */
    public boolean deleteSession(String sessionId) {
        try {
            String filePath = getSessionFilePath(sessionId);
            return FileUtil.del(filePath);
        } catch (Exception e) {
            log.error("Failed to delete session: {}", sessionId, e);
            return false;
        }
    }

    /**
     * 创建新会话
     */
    public ChatSession createNewSession(String title) {
        ChatSession session = new ChatSession();
        session.setId(generateSessionId());
        session.setTitle(title != null ? title : "新对话");
        session.setCreateTime(System.currentTimeMillis());
        session.setUpdateTime(System.currentTimeMillis());
        session.setMessages(new ArrayList<>());
        return session;
    }

    /**
     * 添加消息到会话
     */
    public void addMessage(ChatSession session, String role, String content) {
        SessionMessage message = new SessionMessage(role, content, System.currentTimeMillis());
        session.getMessages().add(message);

        // 如果是第一条用户消息，更新标题
        if ("user".equals(role) && session.getMessages().size() == 1) {
            String title = content.length() > 20 ? content.substring(0, 20) + "..." : content;
            session.setTitle(title);
        }

        saveSession(session);
    }

    /**
     * 获取会话文件路径
     */
    private String getSessionFilePath(String sessionId) {
        return sessionSaveDir + File.separator + sessionId + ".json";
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return "session-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }
}