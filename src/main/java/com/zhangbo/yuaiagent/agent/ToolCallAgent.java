package com.zhangbo.yuaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.zhangbo.yuaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    // 保存模型的直接回答内容（当不需要调用工具时）
    private String directAnswer;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        // 1、校验提示词，拼接用户提示词
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        // 2、调用 AI 大模型，获取工具调用结果
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();
            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;
            // 3、解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 保存助手消息内容
            String assistantMessageContent = assistantMessage.getText();
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            log.info(getName() + " 思考完成，选择了 " + toolCallList.size() + " 个工具");
            if (!toolCallList.isEmpty()) {
                String toolCallInfo = toolCallList.stream()
                        .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                        .collect(Collectors.joining("\n"));
                log.info(toolCallInfo);
            }
            // 如果不需要调用工具，保存直接回答内容
            if (toolCallList.isEmpty()) {
                // 记录助手消息到消息列表
                getMessageList().add(assistantMessage);
                // 保存直接回答内容，供 step() 使用
                this.directAnswer = assistantMessageContent;
                // 设置任务结束状态
                setState(AgentState.FINISHED);
                return false;
            } else {
                // 需要调用工具时，无需记录助手消息，因为调用工具时会自动记录
                this.directAnswer = null;
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题：" + e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            this.directAnswer = "处理时遇到了错误：" + e.getMessage();
            return false;
        }
    }

    /**
     * 执行工具调用并处理结果
     *
     * @return 执行结果
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }
        // 调用工具
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        // 判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            // 任务结束，更改状态
            setState(AgentState.FINISHED);
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response -> {
                    String resultData = String.valueOf(response.responseData());
                    int length = resultData.length();
                    return "工具 " + response.name() + " 执行完成，返回结果长度：" + length + " 字";
                })
                .collect(Collectors.joining("\n"));
        log.info(results);
        return results;
    }

    /**
     * 生成最终答案
     * 让 AI 根据所有工具调用结果，生成一个清晰、直接的答案给用户
     *
     * @return 最终答案
     */
    @Override
    protected String generateFinalAnswer() {
        // 如果有直接回答内容（没有调用工具的情况）
        if (this.directAnswer != null) {
            return this.directAnswer;
        }

        // 如果调用了工具，让 AI 总结结果并给出最终答案
        try {
            // 添加一个请求，让 AI 生成最终答案
            String summaryPrompt = """
                    请根据上述工具调用结果，直接给用户一个清晰、有用的最终答案。
                    不要提及"工具"、"调用"、"抓取"等技术细节。
                    直接呈现用户需要的信息即可。
                    """;
            getMessageList().add(new UserMessage(summaryPrompt));

            Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .call()
                    .chatResponse();

            return chatResponse.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("生成最终答案失败：{}", e.getMessage());
            // 如果生成失败，返回最后一条助手消息
            if (!getMessageList().isEmpty()) {
                Message lastMessage = CollUtil.getLast(getMessageList());
                if (lastMessage instanceof AssistantMessage assistantMessage) {
                    return assistantMessage.getText();
                }
            }
            return "任务已完成，但生成最终答案时出错。";
        }
    }

    /**
     * 生成最终答案（流式输出，逐字返回）
     *
     * @return 最终答案的流式输出
     */
    public Flux<String> generateFinalAnswerStream() {
        // 如果有直接回答内容（没有调用工具的情况）
        if (this.directAnswer != null) {
            return Flux.just(this.directAnswer);
        }

        // 如果调用了工具，让 AI 流式总结结果并给出最终答案
        try {
            // 添加一个请求，让 AI 生成最终答案
            String summaryPrompt = """
                    请根据上述工具调用结果，直接给用户一个清晰、有用的最终答案。
                    不要提及"工具"、"调用"、"抓取"等技术细节。
                    直接呈现用户需要的信息即可。
                    """;
            getMessageList().add(new UserMessage(summaryPrompt));

            Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
            // 流式调用，逐字返回
            return getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .stream()
                    .content();
        } catch (Exception e) {
            log.error("生成最终答案失败：{}", e.getMessage());
            // 如果生成失败，返回最后一条助手消息
            if (!getMessageList().isEmpty()) {
                Message lastMessage = CollUtil.getLast(getMessageList());
                if (lastMessage instanceof AssistantMessage assistantMessage) {
                    return Flux.just(assistantMessage.getText());
                }
            }
            return Flux.just("任务已完成，但生成最终答案时出错。");
        }
    }

    /**
     * 重置状态（清空直接回答缓存）
     */
    @Override
    public void resetState() {
        super.resetState();
        this.directAnswer = null;
        this.toolCallChatResponse = null;
    }
}
