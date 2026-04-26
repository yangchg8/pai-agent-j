package code.chg.agent.lib.memory;

import code.chg.agent.core.memory.state.PersistentLLMMessage;
import code.chg.agent.llm.LLMMessage;
import code.chg.agent.llm.MessageType;
import code.chg.agent.llm.ToolCall;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ChatMemoryRegionRestoreRepairTest
 * @description Provides the ChatMemoryRegionRestoreRepairTest implementation.
 */
public class ChatMemoryRegionRestoreRepairTest {

    @Test
    public void restoreAppendsSyntheticToolResponseWhenCallHasNoResult() {
        ChatMemoryRegion region = new ChatMemoryRegion();
        PersistentLLMMessage assistantWithToolCall = new PersistentLLMMessage(new LLMMessage() {
            @Override
            public String id() {
                return "ai-1";
            }

            @Override
            public MessageType type() {
                return MessageType.AI;
            }

            @Override
            public List<ToolCall> toolCalls() {
                return List.of(new ToolCall() {
                    @Override
                    public String id() {
                        return "tool-call-1";
                    }

                    @Override
                    public String name() {
                        return "shell";
                    }

                    @Override
                    public String arguments() {
                        return "{\"command\":\"pwd\"}";
                    }
                });
            }
        });

        region.restore(List.of(assistantWithToolCall));

        assertEquals(2, region.messages().size());
        assertEquals(MessageType.TOOL, region.messages().get(1).type());
        assertEquals("tool-call-1", region.messages().get(1).toolCallId());
        assertEquals("Tool execution was interrupted before a result was returned.", region.messages().get(1).content());
    }
}