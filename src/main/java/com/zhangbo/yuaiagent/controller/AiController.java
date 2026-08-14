package com.zhangbo.yuaiagent.controller;

import com.zhangbo.yuaiagent.agent.YuManus;
import com.zhangbo.yuaiagent.app.LoveApp;
import com.zhangbo.yuaiagent.session.ChatSession;
import com.zhangbo.yuaiagent.session.SessionManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/ai")
@Slf4j
public class AiController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private SessionManager sessionManager;

    // 保存会话的智能体实例（支持多轮对话记忆）
    private final Map<String, YuManus> agentMap = new ConcurrentHashMap<>();

    /**
     * SSE 流式调用 AI 恋爱大师应用
     *
     * @param message 用户消息
     * @param chatId  对话ID
     * @return 流式响应
     */
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId);
    }

    // ==================== 会话管理接口 ====================

    /**
     * 获取所有会话列表
     */
    @GetMapping("/manus/sessions")
    public List<ChatSession> listSessions() {
        return sessionManager.listSessions();
    }

    /**
     * 创建新会话
     */
    @PostMapping("/manus/session")
    public ChatSession createSession(@RequestParam(required = false) String title) {
        ChatSession session = sessionManager.createNewSession(title);
        sessionManager.saveSession(session);
        return session;
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/manus/session/{sessionId}")
    public ChatSession getSession(@PathVariable String sessionId) {
        return sessionManager.loadSession(sessionId);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/manus/session/{sessionId}")
    public String deleteSession(@PathVariable String sessionId) {
        agentMap.remove(sessionId);
        boolean deleted = sessionManager.deleteSession(sessionId);
        return deleted ? "删除成功" : "删除失败";
    }

    /**
     * 流式调用 Manus 超级智能体（真正的逐字流式输出，支持多轮对话记忆和历史记录）
     *
     * @param message   用户消息
     * @param sessionId 会话ID
     * @return SSE 流式响应
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message, String sessionId) {
        // 如果没有 sessionId，创建新会话
        if (sessionId == null || sessionId.isEmpty()) {
            ChatSession newSession = sessionManager.createNewSession(message);
            sessionManager.saveSession(newSession);
            sessionId = newSession.getId();
        }

        final String finalSessionId = sessionId;

        // 加载会话（如果存在）
        var ref = new Object() {
            ChatSession session = sessionManager.loadSession(finalSessionId);
        };
        if (ref.session == null) {
            ref.session = sessionManager.createNewSession(message);
            sessionManager.saveSession(ref.session);
        }

        // 保存用户消息
        sessionManager.addMessage(ref.session, "user", message);

        // 获取或创建智能体实例
        YuManus yuManus = agentMap.computeIfAbsent(finalSessionId, id -> {
            YuManus agent = new YuManus(allTools, dashscopeChatModel);
            agent.setMaxSteps(20);
            return agent;
        });

        // 重置智能体状态
        yuManus.resetState();

        // 创建 SSE 响应
        SseEmitter sseEmitter = new SseEmitter(300000L);

        // 用于收集完整的 AI 响应（保存到会话）
        StringBuilder fullResponse = new StringBuilder();

        // 异步执行
        new Thread(() -> {
            try {
                // 第一步：执行多步循环（思考→行动→观察）
                yuManus.runSteps(message);

                // 第二步：流式生成最终答案，逐字输出
                yuManus.generateFinalAnswerStream()
                        .subscribe(
                                chunk -> {
                                    try {
                                        // 逐字发送给前端
                                        sseEmitter.send(chunk);
                                        // 收集完整响应
                                        fullResponse.append(chunk);
                                    } catch (IOException e) {
                                        log.error("发送流式数据失败", e);
                                    }
                                },
                                error -> {
                                    try {
                                        sseEmitter.send("错误：" + error.getMessage());
                                        sseEmitter.send("[DONE]");
                                        sseEmitter.complete();
                                    } catch (IOException ex) {
                                        sseEmitter.completeWithError(ex);
                                    }
                                },
                                () -> {
                                    try {
                                        // 保存完整 AI 响应到会话
                                        sessionManager.addMessage(ref.session, "assistant", fullResponse.toString());
                                        // 发送完成标记
                                        sseEmitter.send("[DONE]");
                                        sseEmitter.complete();
                                    } catch (IOException e) {
                                        sseEmitter.completeWithError(e);
                                    } finally {
                                        // 清理智能体资源
                                        yuManus.cleanup();
                                    }
                                }
                        );
            } catch (Exception e) {
                try {
                    sseEmitter.send("错误：" + e.getMessage());
                    sseEmitter.send("[DONE]");
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                } finally {
                    yuManus.cleanup();
                }
            }
        }).start();

        return sseEmitter;
    }
}
