# PAI-Agent

**基于 Java 和 OpenAI SDK 构建的事件驱动 AI Agent 框架，采用 ReAct（Reasoning + Acting）架构，支持会话状态管理、上下文管理、工具权限管理、SKILL 动态加载、MCP 协议。内置了一个 CLI 版本的通用智能体，基于 SQLite 和本地文件做会话持久化。**


---

## 架构概览

PAI-Agent 采用三层架构，渲染层、事件消息层和订阅层清晰分离：

![ReAct 循环 — 事件驱动架构](docs/react-loop.svg)

---

## 核心设计亮点

### 1. 异步事件驱动的扁平化架构

Brain 和 Tool 在同一层级订阅事件总线，实现扁平化、解耦的协调机制，通过可配置线程池并发执行工具，支持自动重试。扁平化的架构设计将 Agent 状态数据的**中断、恢复、持久化时机**统一到事件消息的发布和消费阶段，避免了像 LangGraph 这类基于图的 Agent 框架状态维护困难的问题。

### 2. 响应式记忆区域设计

构建了响应式的记忆区域设计，内置五个专用记忆区域（System、Chat、Plan、Skill、MCP），支持记忆区域绑定对应上下文工具。上下文工具修改了对应记忆区域内容后，会**自动更新模型上下文内容**。响应式的上下文设计极大简化了 LLM 繁杂的上下文拼接操作，当 Token 超过上下文窗口 90% 时自动压缩。

### 3. 基于资源路径的工具权限系统

所有工具的权限统一通过**资源路径（Resource）和权限范围（可读、可写、可执行）**来标识。工具可以通过编写对应资源路径和权限范围在当前工具下的解释，基于 LLM 生成对应授权选项（LLM-Based），也可以基于规则直接指定授权选项（Rule-Based），支持会话级权限累积和全局预授权。

---

## 其他特性

- **注解驱动工具框架** -- 通过 `@Tool`、`@ToolParameter`、`@ToolPermissionChecker` 注解定义工具，基于反射自动发现和注册
- **MCP（模型上下文协议）集成** -- 支持第三方工具，兼容 stdio、SSE 和 Streamable HTTP 三种传输方式，支持动态发现
- **会话持久化** -- 基于 SQLite 的会话存储，原子快照、状态恢复，以及中断工具调用的自动修复
- **流式 CLI** -- 基于 JLine 的终端，实时渲染 LLM 输出、多行输入、命令自动补全、交互式授权提示
- **SKILL 技能系统** -- 面向特定领域的提示词和工具集，从 `.skill.md` 文件加载，动态注入对话上下文
- **LLM 协议抽象** -- OpenAI 兼容接口，支持流式响应、并行工具调用、结构化输出和思维链（Thinking）内容提取

---

## 核心组件

### Brain -- 推理引擎

Brain 是核心协调器，从记忆区域组装 LLM 请求、通过流式调用模型、并将工具调用结果通过事件总线路由回来。

**处理链（Processor 管道模式）：**

| 阶段 | 处理器 | 职责 |
|------|--------|------|
| 1 | `MemoryRegionProcessor` | 根据事件更新各记忆区域状态 |
| 2 | `BrainRunningStateProcessor` | 跟踪工具执行状态和协调 |
| 3 | `MemoryCleanUpProcessor` | 通过 `BrainHook` 触发上下文压缩 |
| 4 | `LLMCallProcessor` | 组装消息 + 工具定义，调用 LLM，流式输出结果 |

### EventMessageBus -- 异步发布/订阅中心

- **基于关注点的路由**：每个订阅者通过 `concern(EventMessage)` 谓词声明感兴趣的消息 -- 无集中式路由表
- **无锁并发调度**：`ConcurrentLinkedQueue` 用于轮内消息路由（无显式锁），`LockSupport.park()/unpark()` 实现线程信号通知，虚拟线程执行器并行执行工具
- **内置重试**：失败消息最多重试 3 次，有界重试队列（最大 512）
- **完成协调**：`AtomicInteger runningMessageCount` 跟踪活跃工具执行数，仅当所有工具完成时 Channel 才关闭

### 记忆系统 -- 五个专用区域

| 区域 | 角色 | 绑定工具 |
|------|------|----------|
| `SystemMemoryRegion` | 系统提示词 + 工具定义（只读） | -- |
| `ChatMemoryRegion` | 对话历史，通过 `BrainSummaryHook` 自动压缩 | -- |
| `PlanMemoryRegion` | 任务计划状态（步骤 + 状态），注入系统提示词 | `PlanTool` |
| `SkillMemoryRegion` | 从 `.skill.md` 文件加载的技能指令 | 技能绑定的工具集 |
| `McpToolMemoryRegion` | MCP 工具（运行时动态加载/卸载） | MCP 发现的工具 |

**上下文压缩**：当 `ChatHistory` Token 数超过 `context_window x 90%` 时，系统收集用户消息（最多 20K tokens），发送摘要提示词给 LLM，用摘要替换历史记录，同时保留最近消息和当前工具结果。

### 工具系统 -- 注解驱动

```java
@Tool(name = "read_file", description = "读取文件内容")
public FileToolResult readFile(
    @ToolParameter(name = "path", description = "文件路径") String path,
    @ToolParameter(name = "start_line", description = "起始行") Integer startLine
) { ... }

@ToolPermissionChecker(toolName = "read_file")
public ToolPermissionResult readFilePermissionCheck(
    ToolPermissionPolicy policy, Object[] arguments
) { ... }
```

**内置工具**：`ShellTool`（含 tree-sitter 静态安全分析）、`FileTool`、`ApplyPatchTool`（结构化差异编辑）、`PlanTool`、`WebFetchTool`，以及动态加载的 MCP 工具。

### 工具权限系统

所有工具的权限统一通过 **资源路径（Resource）+ 权限范围（Operation）** 来标识：
- 资源路径：`FILE:/path`、`DIR:/path/**`、`COMMAND:git`
- 权限范围：`READ`、`WRITE`、`DELETE`、`EXECUTE`、`ALL`

工具可以通过编写对应资源路径和权限范围在当前工具下的语义解释，由 LLM 生成对应授权选项；也可以基于规则直接指定授权选项。

三种授权模式：

| 模式 | 行为 |
|------|------|
| `LLM_BASED` | 工具声明资源路径和权限范围的解释，LLM 据此生成授权选项，用户交互式确认 |
| `RULE_BASED` | 基于规则直接指定授权选项，自动授权或拒绝 |
| `REJECTED` | 直接拒绝，不做进一步处理 |

会话级权限通过用户 `Grant` 操作累积。全局预授权级别允许匹配的操作无需确认即可通过。

### LLM 协议抽象

```java
interface LLMClient {
    List<LLMMessage> chat(LLMRequest request);           // 同步调用
    StreamResponse streamChat(LLMRequest request);        // 流式调用
    <T> T structureResponseChat(String input, Class<T>);  // 结构化输出
}
```

- 默认实现基于 OpenAI Java SDK（`openai-java 4.13.0`）
- 流式响应 + `ChatCompletionAccumulator` + Token 使用量统计
- 支持 o-series 模型的思维链/推理内容提取
- 流式过程中工具调用块（chunk）的缓冲和重组
- 通过实现 `LLMClientFactory` 和 `LLMClient` 接口扩展其他提供商（Anthropic、Gemini 等）

### MCP（模型上下文协议）支持

- 三种传输方式：`stdio`、`SSE`、`Streamable HTTP`
- `McpToolAdapter` 将 MCP 工具桥接到本地 `Tool` 接口
- 动态工具发现，无需重新编译
- 延迟连接：仅在工具被调用时才联系服务器
- 故障隔离到单个工具

### 技能系统

- **技能 = 提示片段 + 工具集**，面向特定任务领域
- `SkillLoader` 通过 BFS 遍历（最大深度 6）从全局（`~/.pai-agent/skills`）和工作区（`.pai-agent/skills`）目录发现 `.skill.md` 文件
- YAML frontmatter 用于元数据（名称、描述、适用范围）
- `SkillMemoryRegion` 将激活技能的指令注入对话上下文

### 会话持久化

- `SessionStoreManager` 接口，默认实现 `LocalSessionStoreManager`（SQLite）
- 原子快照文件 + SQLite 元数据（标题、Token 使用量、时间戳）
- 每会话 `ReentrantLock` 实现线程安全的并发访问
- `StateSubscription` 接口用于订阅者状态保存/恢复
- 会话恢复时自动修复中断的工具调用

### CLI

- **交互模式**：基于 JLine 的持续对话，支持 readline、历史记录和自动补全
- **命令系统**：`/sessions`、`/mcp`、`/skills`、`/compact`、`/clear` 等
- **多行输入**：`Ctrl+J` / `Alt+Enter`
- **授权交互**：`auth>` 提示符，支持 `y`（允许一次）/ `s`（保存授权）/ `n`（拒绝）
- **流式输出**：`LessRenderer` 实时渲染 LLM 内容、工具调用和思维链内容

---

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Java 23（Records、模式匹配、虚拟线程支持） |
| 构建 | Maven |
| LLM 集成 | OpenAI Java SDK `openai-java 4.13.0` |
| MCP 支持 | `mcp-java-sdk 1.1.0`（stdio / SSE / Streamable HTTP） |
| 终端 | JLine 3（`jline 3.27.1`） |
| 会话存储 | SQLite（`sqlite-jdbc 3.50.3.0`） |
| 日志 | SLF4J + Logback |
| 代码简化 | Lombok 1.18.40 |
| JSON/YAML | Jackson 2.17.1 |

---

## 设计模式

| 模式 | 应用场景 |
|------|---------|
| **发布/订阅** | `EventMessageBus` + `Subscription` 事件路由 |
| **处理器管道** | Brain 中的 `EventMessageProcessor` 链 |
| **建造者** | `ReactAgentBuilder`、`DefaultToolBuilder` 流式配置 |
| **策略** | `ToolAuthorizationMode` 实现 |
| **适配器** | `McpToolAdapter` 将 MCP 桥接到本地 Tool 接口 |
| **工厂** | `LLMClientFactory` 创建 LLM 客户端 |
| **仓储** | `SessionStoreManager` 数据访问抽象 |
| **状态机** | `BrainRunningState` 跟踪工具执行生命周期 |
| **命令** | CLI 命令分发和处理器 |

---

## 快速开始

### 环境要求

- **JDK 23+**
- **Maven 3.x**

### 配置

将配置文件复制到 PAI-Agent 主目录下并编辑：

```bash
mkdir -p ~/.pai-agent
cp config.yml ~/.pai-agent/config.yml
```

编辑 `~/.pai-agent/config.yml`，填入你的 API 凭证：

```yaml
openai:
  api:
    key: your-api-key
    baseUrl: https://api.openai.com/v1    # 可选，用于代理/自定义端点
  model:
    name: gpt-4o
    token: 128000
```

### 构建与运行

```bash
# 构建
mvn clean package

# 运行 CLI（交互模式）
java -jar target/pai-agent.jar

# 或使用启动脚本
./start.sh
```

---

## 项目结构

```
src/main/java/code/chg/agent/
├── annotation/          # @Tool, @ToolParameter, @ToolPermissionChecker
├── cli/                 # CLI 入口、命令分发、终端渲染
│   ├── command/         # 命令解析和处理器
│   └── render/          # LessRenderer 流式输出渲染
├── config/              # 配置管理（OpenAI、Agent 设置）
├── core/                # 核心框架抽象
│   ├── agent/           # Agent 接口
│   ├── brain/           # Brain 推理引擎 + 处理链
│   │   └── process/     # MemoryRegion / State / CleanUp / LLMCall 处理器
│   ├── channel/         # ChatChannel、ChannelSubscriber、消息类型
│   ├── event/           # EventMessageBus、Subscription、EventMessage
│   ├── memory/          # MemoryRegion、MemoryRegionHook 接口
│   ├── permission/      # 权限模型、ToolPermissionPolicy
│   ├── session/         # SessionStoreManager 接口
│   └── tool/            # Tool、ToolDefinition、授权模式
├── infa/                # 基础设施实现
│   ├── mcp/             # McpClientSession、McpToolAdapter
│   └── openai/          # OpenAIClientFactory、OpenAI 流式
├── lib/                 # 库实现
│   ├── agent/           # PaiAgent（高层外观）
│   ├── brain/           # BrainSummaryHook（上下文压缩）
│   ├── event/           # LocalEventMessageBus
│   ├── memory/          # ChatMemoryRegion、PlanMemoryRegion 等
│   ├── session/         # LocalSessionStoreManager（SQLite）
│   ├── skill/           # SkillLoader、SkillsManager
│   └── tool/            # ShellTool、FileTool、ApplyPatchTool、PlanTool、WebFetchTool
├── llm/                 # LLMClient、LLMMessage、StreamResponse 抽象
└── utils/               # 工具类
```

---

## 关键代码引用

| 组件        | 源码 |
|-----------|------|
| 事件总线      | [BaseEventMessageBus](src/main/java/code/chg/agent/core/event/BaseEventMessageBus.java) |
| Brain     | [AbstractBrain](src/main/java/code/chg/agent/core/brain/AbstractBrain.java)、[DefaultBrain](src/main/java/code/chg/agent/core/brain/DefaultBrain.java) |
| 处理链       | [LLMCallProcessor](src/main/java/code/chg/agent/core/brain/process/LLMCallProcessor.java) |
| 记忆区域      | [MemoryRegion](src/main/java/code/chg/agent/core/memory/MemoryRegion.java)、[ChatMemoryRegion](src/main/java/code/chg/agent/lib/memory/ChatMemoryRegion.java) |
| 工具注解      | [@Tool](src/main/java/code/chg/agent/annotation/Tool.java)、[@ToolPermissionChecker](src/main/java/code/chg/agent/annotation/ToolPermissionChecker.java) |
| 权限系统      | [ToolPermissionPolicy](src/main/java/code/chg/agent/core/permission/ToolPermissionPolicy.java) |
| LLM 客户端   | [OpenAIClientFactory](src/main/java/code/chg/agent/infa/openai/OpenAIClientFactory.java) |
| MCP 集成    | [McpClientSession](src/main/java/code/chg/agent/infa/mcp/McpClientSession.java)、[McpToolAdapter](src/main/java/code/chg/agent/infa/mcp/McpToolAdapter.java) |
| 会话存储      | [LocalSessionStoreManager](src/main/java/code/chg/agent/lib/session/LocalSessionStoreManager.java) |
| CLI 入口    | [CLI](src/main/java/code/chg/agent/cli/CLI.java) |
| Agent 外观  | [PaiAgent](src/main/java/code/chg/agent/lib/agent/PaiAgent.java) |
| SKILL技能系统 | [SkillLoader](src/main/java/code/chg/agent/lib/skill/SkillLoader.java) |

---

## License

本项目用于个人/学习用途。
