package code.chg.agent.core.brain.state;

import code.chg.agent.core.memory.state.PersistentLLMMessage;
import code.chg.agent.llm.MessageType;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title PersistentBrainMemoryItemTest
 * @description Provides the PersistentBrainMemoryItemTest implementation.
 */
public class PersistentBrainMemoryItemTest {

    @Test
    public void encodeAndDecodeRoundTripMessagesAsSingleLineJson() {
        PersistentLLMMessage first = new PersistentLLMMessage();
        first.setId("msg-1");
        first.setType(MessageType.HUMAN);
        first.setContent("hello");

        PersistentLLMMessage second = new PersistentLLMMessage();
        second.setId("msg-2");
        second.setType(MessageType.SYSTEM);
        second.setContent("world");

        PersistentBrainMemoryItem firstItem = new PersistentBrainMemoryItem();
        firstItem.setMessage(first);
        PersistentBrainMemoryItem secondItem = new PersistentBrainMemoryItem();
        secondItem.setMessage(second);

        byte[] encoded = PersistentBrainMemoryItem.encode(List.of(firstItem, secondItem));

        String encodedText = new String(encoded, StandardCharsets.UTF_8);
        assertEquals(2L, encodedText.lines().count());

        List<PersistentBrainMemoryItem> decoded = PersistentBrainMemoryItem.decode(encoded);

        assertEquals(2, decoded.size());
        assertNull(decoded.get(0).getBrainName());
        assertNull(decoded.get(0).getMemoryRegionName());
        assertEquals("msg-1", decoded.get(0).getMessage().getId());
        assertEquals(MessageType.HUMAN, decoded.get(0).getMessage().getType());
        assertEquals("hello", decoded.get(0).getMessage().getContent());
        assertEquals("msg-2", decoded.get(1).getMessage().getId());
        assertEquals(MessageType.SYSTEM, decoded.get(1).getMessage().getType());
        assertEquals("world", decoded.get(1).getMessage().getContent());
    }

}