# PAI-Agent Framework Mechanisms and Principles

## 1. Overall Architecture

PAI-Agent is an event-driven AI agent framework in the ReAct (Reasoning + Acting) style, built with Java 23 and integrated at the foundation with large models through an OpenAI-compatible protocol.

```
User
   │  talk(message)
   ▼
Agent (ReactAgent / PaiAgent)
   │ publishes HumanEventMessage
   ▼
EventMessageBus (DefaultEventMessageBus)
   │  event routing → matches Subscription.concern()
   ▼
 ┌──────────────────────────────────────┐
 │  Brain (AbstractBrain/DefaultBrain)  │  ← core coordinator
 │   EventMessageExecutor (processing chain) │
 │    ├─ MemoryRegionProcessor          │
 │    ├─ BrainRunningStateProcessor     │
 │    ├─ MemoryCleanUpProcessor         │
 │    └─ LLMCallProcessor               │
 └──────────────────────────────────────┘
        │ publishes ToolEventMessage
        ▼
 ┌─────────────────┐
 │ Tool (AbstractTool)│ ← subscribes to the event bus; concern() matches tool name
 │  ├─ permission check │
 │  └─ execute tool     │
 └─────────────────┘
        │ publishes tool return result
        ▼
EventMessageBus → Brain (continues the next round of reasoning)
```

---

## 2. Core Components

### 1. EventMessageBus (Event Message Bus)

- Interface: `core/event/EventMessageBus`
- Implementation: `core/event/DefaultEventMessageBus`
- Responsibility: the central hub for publishing and subscribing to all events, decoupling Brain and Tool
- Key methods:
  - `talk(EventMessage, ChatChannel)` — initiates one conversation turn
  - `publish(EventMessage)` — publishes intermediate results from a Tool or Brain
  - `subscribe(Subscription)` — registers a listener

### 2. Subscription (Subscriber Interface)

- Interface: `core/event/Subscription`
- `concern(EventMessage)` — declares which message types it is interested in
- `onMessage(EventMessage, EventBusContext, EventMessageBusCallBack)` — receives and processes events
- Both Brain and Tool implement this interface

### 3. Brain (Reasoning Core)

- Abstract base class: `core/brain/AbstractBrain`
- Default implementation: `core/brain/DefaultBrain`
- Responsibility: coordinates memory regions (`MemoryRegion`) → assembles the LLM request → performs streaming invocation → publishes results
- Processing chain (`EventMessageExecutor`):
  1. `MemoryRegionProcessor` — updates each memory region based on the message
  2. `BrainRunningStateProcessor` — tracks tool invocation state
  3. `MemoryCleanUpProcessor` — triggers `BrainHook.cleanUp` (context compression)
  4. `LLMCallProcessor` — assembles messages plus tool definitions, invokes the LLM, and publishes results in a stream

### 4. MemoryRegion (Memory Region)

- Interface: `core/memory/MemoryRegion`
- `SystemMemoryRegion` — carries the system prompt and the list of tool definitions
- `ChatMemoryRegion` — maintains conversation history (Human / AI / ToolResponse)
- `PlanMemoryRegion` — maintains task plan state (steps + status)
- Extension point: responds to events and updates its own state through `MemoryRegionHook`

### 5. Tool

- Interface: `core/tool/Tool` (extends `Subscription` + `ToolDefinition`)
- Abstract base class: `core/tool/AbstractTool`
- Annotation-driven: `@Tool` (description) + `@ToolParameter` (parameters) + `@ToolPermissionChecker` (permissions)
- Tool permission system:
  - `ToolPermissionPolicy` — policy declaration (whitelist/capability scope)
  - `ToolPermissionResult` — check result: direct approval / LLM authorization / rule-based authorization / rejection
  - `ToolAuthorizationMode`:
    - `LLM_BASED` — generates explanatory text and requires secondary confirmation through the user/LLM
    - `RULE_BASED` — permission declaration based on rules
    - `REJECTED` — directly rejected

### 6. LLM Protocol Layer

- `LLMClient` — unified LLM interface (`chat` / `streamChat` / `structureResponseChat`)
- `LLMMessage` — message abstraction (`SYSTEM` / `HUMAN` / `AI` / `TOOL_RESPONSE`)
- `LLMMessageChunk` — streaming chunk (`content + toolCallChunks`)
- `ThinkingChunk` — reasoning-content chunk (`reasoning_content` / `thinking`)
- `StreamResponse` — streaming iterator; after `accumulate`, it calls `completion()`
- OpenAI implementation: `infa/openai/OpenAIClientFactory`

### 7. ChatChannel (Output Channel)

- Interface: `core/channel/ChatChannel`
- Implementation: `core/channel/DefaultChatChannel` (based on `CountDownLatch`, with `await` waiting for completion)
- `ChannelSubscriber` — external subscriber that receives real-time streamed messages
- Message type `ChannelMessageType`:
  - `THINKING_CHUNK` / `THINKING` — chain-of-thought content
  - `LLM_CONTENT_CHUNK` / `LLM_CONTENT` — model output
  - `TOOL_CALL_REQUEST_CHUNK` / `TOOL_CALL_REQUEST` — tool invocation request
  - `TOOL_CALL_RESPONSE` — tool execution result
  - `TOOL_AUTHORIZATION_REQUEST` — permission request
  - `TOOL_CALL_REJECTED` — tool invocation rejected

### 8. BrainHook (Hook Mechanism)

- Interface: `core/brain/BrainHook`
- `BrainSummaryHook` — context compression hook: when tokens exceed the threshold (90% of the context window), it automatically calls the LLM to generate a summary, compresses the history, and preserves the most recent user messages

---

## 3. Tooling System (`lib/tool`)

### Built-in Tools

| Tool | Description |
|---|---|
| `ShellTool` | Executes commands in the local Unix shell, supporting timeouts, output truncation, and static safety analysis |
| `FileTool` | Reads/writes files and lists directories, with differential editing in `apply_patch` mode |
| `ApplyPatchTool` | Precisely modifies files in a structured patch format (`Add` / `Update` / `Delete`) |
| `PlanTool` | Maintains the task plan list (steps + status), working with `PlanMemoryRegion` |
| `WebFetchTool` | Fetches web page content over HTTP (supports `GET`, returns text output) |

### Shell Tool Safety System

- `ShellStaticAnalyzer` — analyzes command intent using tree-sitter syntax parsing
- `CommandSafetyClassifier` — classifies safety levels (`SAFE` / `WARN` / `DANGER`)
- `ShellPermissionCheckDispatcher` — dispatches the safety check chain (whitelist / script execution / unknown command)

---

## 4. Memory System (`lib/memory`)

| Memory Region | Responsibility |
|---|---|
| `SystemMemoryRegion` | System prompt + tool definitions (globally read-only) |
| `ChatMemoryRegion` | Conversation history (Human/AI/Tool), with automatic compression support |
| `PlanMemoryRegion` | Task plan state, injected into the system prompt and updated by tools |

---

## 5. MCP (Model Context Protocol)

- Interface: `lib/mcp/McpClient` — standard JSON-RPC over stdio/SSE MCP client
- `McpToolAdapter` — adapts MCP tools to the `Tool` interface and automatically registers them with the Brain
- `McpServerConfig` — configures an MCP server (`command` / `args` / `env` / `transportType`)
- Supports tool discovery, parameter forwarding, and result return for third-party MCP servers

---

## 6. Skill (Skill / Knowledge Injection)

- A skill is a prompt fragment plus a tool set designed for a specific task scenario
- `SkillLoader` — loads `.skill.md` files from the filesystem or classpath
- `SkillManager` — manages loaded skills and supports activation by task type
- `SkillMemoryRegion` — injects a skill’s system instructions into the conversation context
- A skill includes: description (`description`), applicability scope (`applyTo`), system prompt (`prompt`), and tool bindings

---

## 7. ReAct Loop Workflow

```
Human Message
    │
    ▼
[Brain] assembles prompt (SystemPrompt + ChatHistory + PlanContext + SkillPrompt)
    │
    ▼
LLM reasoning (streaming response)
    │
    ├── no tool call → AI reply content → written to ChatHistory → end
    │
    └── tool call present
            │
            ▼
         permission check (PermissionChecker)
            │
            ├── approved → execute tool → result ToolCallResponse → Brain continues reasoning
            ├── authorization required → publish TOOL_AUTHORIZATION_REQUEST → wait for user response
            └── rejected → return TOOL_CALL_REJECTED → notify the LLM
```

---

## 8. Context Compression (Context Compaction)

- Trigger condition: total tokens in `ChatHistory` exceed `context_window × 90%`
- Compression process:
  1. Collect all user messages in the history (up to 20K tokens)
  2. Send `SUMMARIZATION_PROMPT` to the LLM and obtain a summary
  3. Clear the history, keeping: the summary + recent user messages + tool results from the current turn
- Implementation: `BrainSummaryHook`, registered as a `BrainHook`

---

## 9. Thinking (Chain-of-Thought) Support

- `LLMMessageChunk` extends `thinkingContent()` — supports streamed reasoning content
- `ChannelMessageType.THINKING_CHUNK` — streams reasoning content to the UI
- OpenAI compatibility: supported through the `reasoning_effort` parameter or the `reasoning_content` field
- Anthropic/Claude: supports parsing and exposing `thinking` content blocks

---

## 10. CLI Design

- Entry point: `cli/CLI.java`
- Supports two runtime modes:
  - **Interactive mode** (`Interactive`): reads from stdin, continues the conversation, and supports authorization confirmation
  - **One-shot mode** (`One-shot`): accepts a task through parameters and exits after execution
- Components:
  - `TerminalRenderer` — color terminal output that renders streaming LLM content and tool calls in real time
  - `AuthorizationHandler` — handles permission requests and presents them to the user for confirmation
  - `PaiAgent` — the core agent, integrating all tools and memory