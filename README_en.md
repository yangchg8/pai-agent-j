# PAI-Agent

**A production-grade, event-driven AI Agent framework built with Java 23, implementing the ReAct (Reasoning + Acting) architecture for autonomous task execution.**

PAI-Agent provides a complete infrastructure for building intelligent agents that reason about tasks, invoke tools, manage conversational memory, and interact with users through a streaming CLI -- all coordinated by an asynchronous publish/subscribe event bus.

---

## Highlights

- **Event-Driven Pub/Sub Architecture** -- Brain and Tools subscribe to a central event bus at the same level, enabling flat, decoupled coordination with concurrent tool execution and automatic retry
- **Multi-Region Memory System** -- Five specialized memory regions (System, Chat, Plan, Skill, MCP) with automatic context compression when tokens exceed 90% of the context window
- **Annotation-Driven Tool Framework** -- Define tools with `@Tool`, `@ToolParameter`, and `@ToolPermissionChecker` annotations; discovered and registered automatically via reflection
- **Three-Mode Authorization System** -- LLM-based, rule-based, and direct-rejection authorization modes with session-level permission accumulation and global pre-authorization
- **MCP (Model Context Protocol) Integration** -- First-class support for third-party tools via stdio, SSE, and Streamable HTTP transports with dynamic discovery
- **Session Persistence** -- SQLite-backed session storage with atomic snapshots, state restoration, and automatic repair of interrupted tool calls
- **Streaming CLI** -- JLine-based terminal with real-time LLM output rendering, multiline input, command auto-completion, and interactive authorization prompts
- **Skill System** -- Domain-specific prompt and tool bundles loaded from `.skill.md` files, dynamically injected into conversation context
- **LLM Protocol Abstraction** -- OpenAI-compatible interface supporting streaming responses, parallel tool calls, structured output, and chain-of-thought (thinking) content extraction

---

## Architecture Overview

PAI-Agent follows a three-layer architecture where the Render Layer, Event Message Layer, and Subscription Layer are cleanly separated:

```
┌───────────────────────────────────────────────────────┐
│                    Render Layer                        │
│   CLI (JLine) / LessRenderer / TerminalRenderer       │
│   Receives streaming messages via ChatChannel          │
└─────────────────────┬─────────────────────────────────┘
                      │ ChatChannel
┌─────────────────────▼─────────────────────────────────┐
│               Event Message Layer                      │
│   EventMessageBus (Pub/Sub Hub)                        │
│    - talk(message, channel)   initiate conversation     │
│    - publish(message)        route intermediate results│
│    - subscribe(subscription) register listeners        │
│    - Lock-free dispatch: virtual-thread consumer + ConcurrentLinkedQueue │
└─────────────────────┬─────────────────────────────────┘
                      │ Subscription.concern()
┌─────────────────────▼─────────────────────────────────┐
│                Subscription Layer                      │
│                                                        │
│  ┌──────────────────┐    ┌──────────────────────────┐ │
│  │ Brain             │    │ Tools (Flat Subscribers)  │ │
│  │  Reasoning Engine │    │  - ShellTool             │ │
│  │  Memory Regions   │    │  - FileTool              │ │
│  │  LLM Invocation   │    │  - ApplyPatchTool        │ │
│  │  Processing Chain │    │  - PlanTool              │ │
│  └──────────────────┘    │  - WebFetchTool           │ │
│                           │  - MCP Tools (Dynamic)    │ │
│                           └──────────────────────────┘ │
└────────────────────────────────────────────────────────┘
```

### ReAct Loop

```
User Message
    │
    ▼
[Brain] Assemble prompt (System + Chat History + Plan + Skill)
    │
    ▼
LLM Reasoning (streaming response)
    │
    ├── No tool call → AI reply → write to ChatHistory → end
    │
    └── Tool call detected
            │
            ▼
        Permission Check (PermissionChecker)
            │
            ├── Granted      → Execute tool → ToolCallResponse → Brain continues reasoning
            ├── Authorization → Publish TOOL_AUTHORIZATION_REQUEST → Wait for user
            └── Rejected     → Publish TOOL_CALL_REJECTED → Notify LLM
```

---

## Core Components

### Brain -- Reasoning Engine

The Brain is the central coordinator that assembles LLM requests from memory regions, invokes the model via streaming, and routes tool call results back through the event bus.

**Processing Chain (Processor Pattern):**

| Stage | Processor | Responsibility |
|-------|-----------|----------------|
| 1 | `MemoryRegionProcessor` | Update each memory region based on the incoming event |
| 2 | `BrainRunningStateProcessor` | Track tool execution state and coordination |
| 3 | `MemoryCleanUpProcessor` | Trigger context compression via `BrainHook` |
| 4 | `LLMCallProcessor` | Assemble messages + tool definitions, invoke LLM, stream results |

### EventMessageBus -- Async Pub/Sub Hub

- **Concern-based routing**: Each subscriber declares interest via `concern(EventMessage)` predicates -- no centralized routing table
- **Lock-free concurrent dispatch**: `ConcurrentLinkedQueue` for intra-turn message routing (no explicit locks), `LockSupport.park()/unpark()` for thread signaling, and a virtual-thread-per-task executor for parallel tool execution
- **Built-in retry**: Failed messages retry up to 3 times with a bounded retry queue (512 max)
- **Completion coordination**: `AtomicInteger runningMessageCount` tracks active tool executions; channel closes only when all tools finish

### Memory System -- Five Specialized Regions

| Region | Role | Bound Tools |
|--------|------|-------------|
| `SystemMemoryRegion` | System prompt + tool definitions (read-only) | -- |
| `ChatMemoryRegion` | Conversation history with auto-compaction via `BrainSummaryHook` | -- |
| `PlanMemoryRegion` | Task plan state (steps + status), injected into system prompt | `PlanTool` |
| `SkillMemoryRegion` | Skill instructions loaded from `.skill.md` files | Skill-bound tools |
| `McpToolMemoryRegion` | MCP tools (dynamic load/unload at runtime) | MCP-discovered tools |

**Context Compaction**: When `ChatHistory` tokens exceed `context_window x 90%`, the system collects user messages (up to 20K tokens), sends a summarization prompt to the LLM, and replaces history with the summary while preserving recent messages and current tool results.

### Tool System -- Annotation-Driven

```java
@Tool(name = "read_file", description = "Read file contents")
public FileToolResult readFile(
    @ToolParameter(name = "path", description = "File path") String path,
    @ToolParameter(name = "start_line", description = "Start line") Integer startLine
) { ... }

@ToolPermissionChecker(toolName = "read_file")
public ToolPermissionResult readFilePermissionCheck(
    ToolPermissionPolicy policy, Object[] arguments
) { ... }
```

**Built-in tools**: `ShellTool` (with tree-sitter static safety analysis), `FileTool`, `ApplyPatchTool` (structured diff editing), `PlanTool`, `WebFetchTool`, plus dynamically loaded MCP tools.

### Tool Permission System

Permissions are modeled as **Resource + Operation**:
- Resources: `FILE:/path`, `DIR:/path/**`, `COMMAND:git`
- Operations: `READ`, `WRITE`, `DELETE`, `EXECUTE`, `ALL`

Three authorization modes:

| Mode | Behavior |
|------|----------|
| `LLM_BASED` | LLM generates explanation; user confirms interactively |
| `RULE_BASED` | Pre-defined rules authorize or reject automatically |
| `REJECTED` | Direct rejection without further processing |

Session-level permissions accumulate via user `Grant` actions. Global pre-authorization levels allow matching operations to proceed without confirmation.

### LLM Protocol Abstraction

```java
interface LLMClient {
    List<LLMMessage> chat(LLMRequest request);           // Synchronous
    StreamResponse streamChat(LLMRequest request);        // Streaming
    <T> T structureResponseChat(String input, Class<T>);  // Structured output
}
```

- Default implementation via OpenAI Java SDK (`openai-java 4.13.0`)
- Streaming with `ChatCompletionAccumulator` and token usage tracking
- Thinking/reasoning content extraction for o-series models
- Tool call chunk buffering and reconstruction during streaming
- Extend via `LLMClientFactory` and `LLMClient` interfaces for other providers (Anthropic, Gemini, etc.)

### MCP (Model Context Protocol) Support

- Three transports: `stdio`, `SSE`, `Streamable HTTP`
- `McpToolAdapter` bridges MCP tools to the local `Tool` interface
- Dynamic tool discovery without recompilation
- Lazy connection: servers contacted only when tools are invoked
- Failures isolated to individual tools

### Skill System

- **Skill = Prompt fragment + Tool set** for a specific task domain
- `SkillLoader` discovers `.skill.md` files via BFS traversal (max depth 6) from global (`~/.pai-agent/skills`) and workspace (`.pai-agent/skills`) directories
- YAML frontmatter for metadata (name, description, applicability)
- `SkillMemoryRegion` injects active skill instructions into conversation context

### Session Persistence

- `SessionStoreManager` interface with `LocalSessionStoreManager` (SQLite) default
- Atomic snapshot files + SQLite metadata (title, token usage, timestamps)
- Per-session `ReentrantLock` for thread-safe concurrent access
- `StateSubscription` interface for subscriber state save/restore
- Automatic repair of interrupted tool calls on session restore

### CLI

- **Interactive mode**: Continuous conversation with JLine readline, history, and auto-completion
- **Command system**: `/sessions`, `/mcp`, `/skills`, `/compact`, `/clear`, etc.
- **Multiline input**: `Ctrl+J` / `Alt+Enter`
- **Authorization interaction**: `auth>` prompt with `y` (once) / `s` (save) / `n` (reject)
- **Streaming output**: `LessRenderer` renders LLM content, tool calls, and thinking content in real time

---

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 23 (records, pattern matching, virtual threads support) |
| Build | Maven |
| LLM Integration | OpenAI Java SDK `openai-java 4.13.0` |
| MCP Support | `mcp-java-sdk 1.1.0` (stdio / SSE / Streamable HTTP) |
| Terminal | JLine 3 (`jline 3.27.1`) |
| Session Storage | SQLite (`sqlite-jdbc 3.50.3.0`) |
| Logging | SLF4J + Logback |
| Code Generation | Lombok 1.18.40 |
| JSON/YAML | Jackson 2.17.1 |

---

## Design Patterns

| Pattern | Usage |
|---------|-------|
| **Pub/Sub** | `EventMessageBus` + `Subscription` for event routing |
| **Processor Pipeline** | `EventMessageProcessor` chain in Brain |
| **Builder** | `ReactAgentBuilder`, `DefaultToolBuilder` for fluent configuration |
| **Strategy** | `ToolAuthorizationMode` implementations |
| **Adapter** | `McpToolAdapter` bridging MCP to local Tool interface |
| **Factory** | `LLMClientFactory` for LLM client creation |
| **Repository** | `SessionStoreManager` for data access abstraction |
| **State Machine** | `BrainRunningState` tracking tool execution lifecycle |
| **Command** | CLI command dispatch and handlers |

---

## Quick Start

### Prerequisites

- **JDK 23+**
- **Maven 3.x**

### Configuration

Copy the configuration file to the PAI-Agent home directory and edit it:

```bash
mkdir -p ~/.pai-agent
cp config.yml ~/.pai-agent/config.yml
```

Edit `~/.pai-agent/config.yml` with your API credentials:

```yaml
openai:
  api:
    key: your-api-key
    baseUrl: https://api.openai.com/v1    # Optional, for proxy/custom endpoints
  model:
    name: gpt-4o
    token: 128000
```

### Build & Run

```bash
# Build
mvn clean package

# Run CLI (interactive mode)
java -jar target/pai-agent.jar

# Or use the startup script
./start.sh
```

---

## Project Structure

```
src/main/java/code/chg/agent/
├── annotation/          # @Tool, @ToolParameter, @ToolPermissionChecker
├── cli/                 # CLI entry point, command dispatch, terminal rendering
│   ├── command/         # Command parsing and handlers
│   └── render/          # LessRenderer for streaming output
├── config/              # Configuration management (OpenAI, Agent settings)
├── core/                # Core framework abstractions
│   ├── agent/           # Agent interface
│   ├── brain/           # Brain reasoning engine + processing chain
│   │   └── process/     # MemoryRegion / State / CleanUp / LLMCall processors
│   ├── channel/         # ChatChannel, ChannelSubscriber, message types
│   ├── event/           # EventMessageBus, Subscription, EventMessage
│   ├── memory/          # MemoryRegion, MemoryRegionHook interfaces
│   ├── permission/      # Permission model, ToolPermissionPolicy
│   ├── session/         # SessionStoreManager interface
│   └── tool/            # Tool, ToolDefinition, authorization modes
├── infa/                # Infrastructure implementations
│   ├── mcp/             # McpClientSession, McpToolAdapter
│   └── openai/          # OpenAIClientFactory, OpenAI streaming
├── lib/                 # Library implementations
│   ├── agent/           # PaiAgent (high-level facade)
│   ├── brain/           # BrainSummaryHook (context compression)
│   ├── event/           # LocalEventMessageBus
│   ├── memory/          # ChatMemoryRegion, PlanMemoryRegion, etc.
│   ├── session/         # LocalSessionStoreManager (SQLite)
│   ├── skill/           # SkillLoader, SkillsManager
│   └── tool/            # ShellTool, FileTool, ApplyPatchTool, PlanTool, WebFetchTool
├── llm/                 # LLMClient, LLMMessage, StreamResponse abstractions
└── utils/               # Utility classes
```

---

## Key Code References

| Component | Source |
|-----------|--------|
| Event Bus | [BaseEventMessageBus](src/main/java/code/chg/agent/core/event/BaseEventMessageBus.java) |
| Brain | [AbstractBrain](src/main/java/code/chg/agent/core/brain/AbstractBrain.java), [DefaultBrain](src/main/java/code/chg/agent/core/brain/DefaultBrain.java) |
| Processing Chain | [LLMCallProcessor](src/main/java/code/chg/agent/core/brain/process/LLMCallProcessor.java) |
| Memory Region | [MemoryRegion](src/main/java/code/chg/agent/core/memory/MemoryRegion.java), [ChatMemoryRegion](src/main/java/code/chg/agent/lib/memory/ChatMemoryRegion.java) |
| Tool Annotations | [@Tool](src/main/java/code/chg/agent/annotation/Tool.java), [@ToolPermissionChecker](src/main/java/code/chg/agent/annotation/ToolPermissionChecker.java) |
| Permission System | [ToolPermissionPolicy](src/main/java/code/chg/agent/core/permission/ToolPermissionPolicy.java) |
| LLM Client | [OpenAIClientFactory](src/main/java/code/chg/agent/infa/openai/OpenAIClientFactory.java) |
| MCP Integration | [McpClientSession](src/main/java/code/chg/agent/infa/mcp/McpClientSession.java), [McpToolAdapter](src/main/java/code/chg/agent/infa/mcp/McpToolAdapter.java) |
| Session Storage | [LocalSessionStoreManager](src/main/java/code/chg/agent/lib/session/LocalSessionStoreManager.java) |
| CLI Entry | [CLI](src/main/java/code/chg/agent/cli/CLI.java) |
| Agent Facade | [PaiAgent](src/main/java/code/chg/agent/lib/agent/PaiAgent.java) |
| Skill System | [SkillLoader](src/main/java/code/chg/agent/lib/skill/SkillLoader.java) |

---

## License

This project is for personal/educational use.
