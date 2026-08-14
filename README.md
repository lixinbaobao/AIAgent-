# Yu-AI-Agent 多智能体对话系统

基于 Spring AI 与 ReAct 推理模式的多智能体对话系统，集成 RAG 知识库检索、Function Calling 工具调用、MCP 协议扩展、SSE 流式输出与会话持久化，支持多轮上下文记忆与文件知识问答，智能体可自主规划并调用工具完成复杂任务。

## 技术栈

| 分类 | 技术 |
|------|------|
| 基础框架 | Spring Boot 3.4.4、JDK 21 |
| AI 框架 | Spring AI 1.0.0、Spring AI Alibaba 1.0.0.2（DashScope） |
| 大模型 | 通义千问 qwen-plus、Ollama（本地部署备选） |
| 智能体 | ReAct 模式、四层 Agent 抽象、自主规划 |
| RAG | Markdown 结构化解析、Token 级分块、Embedding 向量化、SimpleVectorStore / PgVector |
| 工具调用 | Function Calling、6 个内置工具、MCP Client |
| 流式输出 | SSE + Flux、逐字响应 |
| 会话持久化 | Kryo 序列化、JSON 文件存储、SessionManager |
| 其他 | Hutool、Knife4j、Jsoup、MinIO（备选） |

## 核心功能

### 1. 自主规划智能体（YuManus）
- 基于 ReAct（思考-行动-观察）模式的四层智能体抽象：BaseAgent → ReActAgent → ToolCallAgent → YuManus
- 支持最多 20 步自主工具调用与状态机管理（IDLE / RUNNING / FINISHED / ERROR）
- 禁用 Spring AI 内置工具执行，自定义 ToolCallingManager 控制完整调用流程
- 支持 directAnswer 短路优化与 TerminateTool 主动终止

### 2. RAG 知识库问答
- 完整流水线：文件上传 → Markdown 结构化解析 → Token 级分块 → LLM 关键词增强 → Embedding 向量化 → 向量存储 → 相似度检索
- 支持 Markdown / 纯文本等多格式文档
- 支持查询重写（QueryRewriter）、元数据过滤（FilterExpressionBuilder）、空上下文兜底
- 提供阿里云 DashScope 知识库服务与自建 PgVector 两种备选方案

### 3. 流式对话引擎
- 基于 SseEmitter + Flux 实现逐字流式输出
- 首字响应延迟 < 1s（无工具调用场景）
- 异步线程模型保障长对话稳定性，超时阈值 300s
- 支持完整响应收集与会话历史持久化

### 4. 会话持久化
- 基于 Kryo 高性能序列化实现多轮对话记忆的文件级持久化
- SessionManager 统一管理会话创建 / 加载 / 删除 / 消息追加
- 支持跨重启上下文恢复，按更新时间倒序排列会话列表

### 5. 工具调用与 MCP 扩展
- 6 个内置工具：文件操作、联网搜索（百度/Google 双引擎）、网页抓取、资源下载、终端操作、任务终止
- 接入 Spring AI MCP Client，自研图片搜索 MCP Server（Pexels API）
- 支持 Function Calling 动态工具注册与回调

### 6. 恋爱大师应用（LoveApp）
- 多轮对话 + 滑动窗口记忆（MessageWindowChatMemory）
- 结构化输出（恋爱报告）
- RAG 知识库问答、工具调用、MCP 服务多种能力组合

## 项目亮点

### 启动性能优化
针对 SimpleVectorStore 每次重启全量重定向量化导致启动耗时 832s 的问题：
- 引入向量库本地 JSON 持久化（SimpleVectorStore.save / load）
- 异步加载机制，启动后后台线程加载知识库，不阻塞 Tomcat 启动
- 新增文件实时持久化，清空知识库同步删除持久化文件
- 优化后启动时间降至秒级

### 自定义 Advisor 机制
- MyLoggerAdvisor：实现 CallAdvisor + StreamAdvisor，流式响应通过 ChatClientMessageAggregator 聚合后打印日志
- ReReadingAdvisor（Re2）：请求前重复问题提示，提升推理准确率
- 支持 Advisor 责任链按 order 排序执行

## 项目结构

```
yu-ai-agent/
├── src/main/java/com/zhangbo/yuaiagent/
│   ├── agent/              # 智能体核心（四层继承体系）
│   ├── app/                # 应用封装（LoveApp 恋爱大师）
│   ├── advisor/            # 自定义 Advisor
│   ├── chatmemory/         # 对话记忆（Kryo 文件持久化）
│   ├── config/             # 配置类（跨域等）
│   ├── constant/           # 常量
│   ├── controller/         # HTTP 接口（AI 对话、文件管理）
│   ├── rag/                # RAG 模块（文本分割、向量存储、查询重写）
│   ├── service/            # 业务服务（知识库管理）
│   ├── session/            # 会话管理
│   └── tools/              # 工具集（6 个工具 + 注册）
├── yu-image-search-mcp-server/  # 独立 MCP Server（图片搜索）
├── yu-ai-agent-frontend/        # 前端项目（Vue3）
└── src/main/resources/
    ├── application.yml     # 主配置
    └── mcp-servers.json    # MCP Server 配置
```

## 快速开始

### 环境要求
- JDK 21+
- Maven 3.8+
- DashScope API Key（通义千问）

### 配置
修改 `src/main/resources/application.yml`：
```yaml
spring:
  ai:
    dashscope:
      api-key: 你的DashScope API Key
      chat:
        options:
          model: qwen-plus
```

### 启动
```bash
# 后端
mvn spring-boot:run

# 前端（可选）
cd yu-ai-agent-frontend
npm install
npm run dev
```

后端启动后访问：http://localhost:8123/api/swagger-ui.html

## 核心接口

| 接口 | 方法 | 说明 |
|------|------|------|
| /ai/manus/chat | GET | 超级智能体对话（SSE 流式） |
| /ai/manus/sessions | GET/POST/DELETE | 会话管理 |
| /ai/love_app/chat/sse | GET | 恋爱大师对话（SSE 流式） |
| /file/upload | POST | 文件上传（自动加入知识库） |
| /file/knowledge/search | GET | 知识库检索 |
| /file/knowledge/rebuild | POST | 重建知识库 |
| /health | GET | 健康检查 |

## 开发说明

- 开发阶段使用 SimpleVectorStore 内存向量库 + 本地 JSON 持久化，无需数据库
- 如需切换 PgVector：启动类去掉 `exclude = DataSourceAutoConfiguration`，打开配置文件中的 datasource 和 pgvector 配置
- MCP 功能默认注释，如需启用打开配置文件中的 mcp client 配置并启动对应 MCP Server
- API Key 请通过环境变量或配置中心管理，避免硬编码提交
