package code.chg.agent.lib.memory;

import code.chg.agent.core.event.EventMessage;
import code.chg.agent.core.event.EventMessageType;
import code.chg.agent.core.event.body.EventBody;
import code.chg.agent.core.event.body.HumanEventBody;
import code.chg.agent.core.event.body.ToolEventBody;
import code.chg.agent.core.memory.MemoryRegionHook;
import code.chg.agent.core.memory.state.PersistentLLMMessage;
import code.chg.agent.llm.LLMMessage;
import code.chg.agent.llm.MessageType;
import code.chg.agent.llm.ToolCall;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link ChatMemoryRegion}: hook behavior, restore/repair logic, and state round-trip.
 * <p>
 * Uses hand-written mocks following existing project test patterns (no Mockito).
 */
public class ChatMemoryRegionTest {

    // ────────────────────── Helper Factories ──────────────────────

    private static EventMessage humanEvent(String id, String content) {
        return new EventMessage() {
            @Override
            public String id() { return id; }

            @Override
            public EventMessageType type() { return EventMessageType.HUMAN_MESSAGE; }

            @Override
            public EventBody body() { return new HumanEventBody(content); }
        };
    }

    private static EventMessage toolResponseEvent(String id, String toolCallId, String response) {
        ToolCall toolCall = new ToolCall() {
            @Override
            public String id() { return toolCallId; }

            @Override
            public String name() { return "test_tool"; }

            @Override
            public String arguments() { return "{}"; }
        };
        return new EventMessage() {
            @Override
            public String id() { return id; }

            @Override
            public EventMessageType type() { return EventMessageType.TOOL_CALL_RESPONSE; }

            @Override
            public EventBody body() { return new ToolEventBody(toolCall, response); }
        };
    }

    private static EventMessage agentEvent(String id) {
        return new EventMessage() {
            @Override
            public String id() { return id; }

            @Override
            public EventMessageType type() { return EventMessageType.AGENT_MESSAGE; }

            @Override
            public EventBody body() { return null; }
        };
    }

    private static LLMMessage aiMessage(String id, String content) {
        return new LLMMessage() {
            @Override
            public String id() { return id; }

            @Override
            public MessageType type() { return MessageType.AI; }

            @Override
            public String content() { return content; }
        };
    }

    private static PersistentLLMMessage persistentAiWithToolCalls(String id, List<ToolCall> toolCalls) {
        return new PersistentLLMMessage(new LLMMessage() {
            @Override
            public String id() { return id; }

            @Override
            public MessageType type() { return MessageType.AI; }

            @Override
            public List<ToolCall> toolCalls() { return toolCalls; }
        });
    }

    private static PersistentLLMMessage persistentToolResponse(String id, String toolCallId, String content) {
        return new PersistentLLMMessage(new LLMMessage() {
            @Override
            public String id() { return id; }

            @Override
            public MessageType type() { return MessageType.TOOL; }

            @Override
            public String toolCallId() { return toolCallId; }

            @Override
            public String content() { return content; }
        });
    }

    private static ToolCall simpleToolCall(String id) {
        return new ToolCall() {
            @Override
            public String id() { return id; }

            @Override
            public String name() { return "shell"; }

            @Override
            public String arguments() { return "{\"command\":\"ls\"}"; }
        };
    }

    // ────────────────────── Hook: onEventMessage ──────────────────────

    @Test
    public void hookAddsHumanMessage() {
        ChatMemoryRegion region = new ChatMemoryRegion();
        MemoryRegionHook hook = region.getHook();

        hook.onEventMessage(humanEvent("h-1", "hello world"));

        assertEquals(1, region.messages().size());
        assertEquals(MessageType.HUMAN, region.messages().get(0).type());
        assertEquals("hello world", region.messages().get(0).content());
        assertEquals("h-1", region.messages().get(0).id());
    }

    @Test
    public void hookAddsToolCallResponse() {
        ChatMemoryRegion region = new ChatMemoryRegion();
        MemoryRegionHook hook = region.getHook();

        hook.onEventMessage(toolResponseEvent("tr-1", "tc-1", "result output"));

        assertEquals(1, region.messages().size());
        assertEquals(MessageType.TOOL, region.messages().get(0).type());
        assertEquals("tc-1", region.messages().get(0).toolCallId());
        assertEquals("result output", region.messages().get(0).content());
    }

    @Test
    public void hookIgnoresUnrelatedEventTypes() {
        ChatMemoryRegion region = new ChatMemoryRegion();
        MemoryRegionHook hook = region.getHook();

        hook.onEventMessage(agentEvent("a-1"));

        assertEquals(0, region.messages().size());
    }

    // ────────────────────── Hook: afterEventMessage ──────────────────────

    @Test
    public void afterEventMessageAppendsLLMMessages() {
        ChatMemoryRegion region = new ChatMemoryRegion();
        MemoryRegionHook hook = region.getHook();

        LLMMessage ai1 = aiMessage("ai-1", "response 1");
        LLMMessage ai2 = aiMessage("ai-2", "response 2");
        hook.afterEventMessage(List.of(ai1, ai2));

        assertEquals(2, region.messages().size());
        assertEquals("response 1", region.messages().get(0).content());
        assertEquals("response 2", region.messages().get(1).content());
    }

    // ────────────────────── Restore ──────────────────────

    @Test
    public void restoreWithNullIsNoOp() {
        ChatMemoryRegion region = new ChatMemoryRegion();
        // Add a message first, then restore with null — should not crash, history stays
        region.getHook().onEventMessage(humanEvent("h-0", "existing"));
        region.restore(null);

        // History should remain unchanged (restore with null is a no-op per the code)
        assertEquals(1, region.messages().size());
    }

    @Test
    public void restoreRepairsMultipleInterruptedToolCalls() {
        ChatMemoryRegion region = new ChatMemoryRegion();

        // AI message with 3 tool calls
        List<ToolCall> toolCalls = List.of(
                simpleToolCall("tc-1"),
                simpleToolCall("tc-2"),
                simpleToolCall("tc-3")
        );
        PersistentLLMMessage aiWithCalls = persistentAiWithToolCalls("ai-1", toolCalls);

        // Only tc-1 has a response
        PersistentLLMMessage response1 = persistentToolResponse("tr-1", "tc-1", "output-1");

        region.restore(List.of(aiWithCalls, response1));

        // Should have: 1 AI + 1 real response + 2 synthetic responses = 4
        assertEquals(4, region.messages().size());

        // Verify synthetic responses for tc-2 and tc-3
        long syntheticCount = region.messages().stream()
                .filter(m -> m.type() == MessageType.TOOL)
                .filter(m -> "Tool execution was interrupted before a result was returned.".equals(m.content()))
                .count();
        assertEquals(2, syntheticCount);
    }

    @Test
    public void restoreDoesNotDuplicateExistingToolResponses() {
        ChatMemoryRegion region = new ChatMemoryRegion();

        List<ToolCall> toolCalls = List.of(simpleToolCall("tc-1"), simpleToolCall("tc-2"));
        PersistentLLMMessage aiWithCalls = persistentAiWithToolCalls("ai-1", toolCalls);
        PersistentLLMMessage response1 = persistentToolResponse("tr-1", "tc-1", "output-1");
        PersistentLLMMessage response2 = persistentToolResponse("tr-2", "tc-2", "output-2");

        region.restore(List.of(aiWithCalls, response1, response2));

        // All tool calls have responses — no synthetic messages should be added
        assertEquals(3, region.messages().size());
        long syntheticCount = region.messages().stream()
                .filter(m -> "Tool execution was interrupted before a result was returned.".equals(m.content()))
                .count();
        assertEquals(0, syntheticCount);
    }

    // ────────────────────── State Round-Trip ──────────────────────

    @Test
    public void getStateRoundTrip() {
        ChatMemoryRegion original = new ChatMemoryRegion();
        MemoryRegionHook hook = original.getHook();

        // Add a human message and an AI response
        hook.onEventMessage(humanEvent("h-1", "user question"));
        hook.afterEventMessage(List.of(aiMessage("ai-1", "ai answer")));

        assertEquals(2, original.messages().size());

        // Save state
        List<PersistentLLMMessage> state = original.getState();
        assertNotNull(state);
        assertEquals(2, state.size());

        // Restore into a new region
        ChatMemoryRegion restored = new ChatMemoryRegion();
        restored.restore(state);

        assertEquals(2, restored.messages().size());
        assertEquals("user question", restored.messages().get(0).content());
        assertEquals(MessageType.HUMAN, restored.messages().get(0).type());
        assertEquals("ai answer", restored.messages().get(1).content());
        assertEquals(MessageType.AI, restored.messages().get(1).type());
    }

    @Test
    public void getStateOnFreshRegionReturnsEmptyList() {
        ChatMemoryRegion region = new ChatMemoryRegion();
        List<PersistentLLMMessage> state = region.getState();
        assertNotNull(state);
        assertTrue(state.isEmpty());
    }

    @Test
    public void regionNameIsChatHistory() {
        ChatMemoryRegion region = new ChatMemoryRegion();
        assertEquals("CHAT_HISTORY", region.getName());
    }
}
