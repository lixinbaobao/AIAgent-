package com.zhangbo.yuaiagent.agent;

import com.zhangbo.yuaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 鱼皮的 AI 超级智能体（拥有自主规划能力，可以直接使用）
 */
@Component
public class YuManus extends ToolCallAgent {

    public YuManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("yuManus");
        String SYSTEM_PROMPT = """
                你是 YuManus，一个全能的 AI 智能助手，旨在解决用户提出的任何任务。
                你拥有多种工具可以使用，请根据任务复杂度灵活应对。

                重要规则：
                1. 对于简单问题（如数学计算、常识问答、日常对话），直接回答，无需使用工具
                2. 对于需要搜索信息、下载资源、操作文件等复杂任务，主动调用相应工具
                3. 当用户询问新闻、时事、最新动态时，必须使用 searchNews 工具获取最新信息
                4. searchNews 用于获取最新新闻，searchWeb 用于普通信息搜索
                5. 完成复杂任务后，调用 terminate 工具结束
                6. 如果不需要使用工具，直接给出答案即可，不要强行调用工具
                7. 最终回答用户时，直接给出清晰的答案，不要提及"工具"、"调用"、"抓取"等技术细节
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                根据用户需求判断：
                - 如果是简单问题（如"1+1等于多少"、"你好"等），直接回答，不要调用任何工具
                - 如果用户询问新闻或最新动态，使用 searchNews 工具
                - 如果是复杂任务（如搜索信息、下载资源等），选择最合适的工具来完成任务
                - 完成任务后，用简洁清晰的语言直接回答用户的问题，不要提及工具调用过程

                请根据实际情况灵活处理。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
