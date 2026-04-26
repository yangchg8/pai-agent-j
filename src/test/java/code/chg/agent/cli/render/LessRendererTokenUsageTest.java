package code.chg.agent.cli.render;

import code.chg.agent.core.channel.ChannelMessageBuilder;
import code.chg.agent.core.channel.ChannelMessageType;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LessRendererTokenUsageTest
 * @description Provides the LessRendererTokenUsageTest implementation.
 */
public class LessRendererTokenUsageTest {

    @Test
    public void tokenUsageShouldRenderInsideAssistantBoxBottomRight() throws Exception {
        try (Terminal terminal = TerminalBuilder.builder()
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .dumb(true)
                .build()) {
            CapturingLiveRegion liveRegion = new CapturingLiveRegion();
            LessRenderer renderer = new LessRenderer(terminal, liveRegion);

            renderer.onMessage(ChannelMessageBuilder.builder("msg-1", "assistant", ChannelMessageType.LLM_CONTENT_CHUNK)
                    .contentChunk("Hello")
                    .build(true));
            renderer.onMessage(ChannelMessageBuilder.builder("token-1", "assistant", ChannelMessageType.TOKEN_USAGE)
                    .tokenUsage(120, 30, 150, 2048, 128000, "OPENAI_USAGE")
                    .build(true));

            String rendered = String.join("\n", liveRegion.lines);
            assertTrue(rendered.contains("+ assistant"));
            assertTrue(rendered.contains("Tokens: 2048/128000"));
            assertTrue(!rendered.contains("token usage"));
            assertTrue(rendered.contains("Hello"));
        }
    }

    private static final class CapturingLiveRegion implements LessRenderer.LiveRegion {
        private List<String> lines = new ArrayList<>();

        @Override
        public void markAnchor() {
        }

        @Override
        public void update(List<String> lines) {
            this.lines = lines == null ? List.of() : List.copyOf(lines);
        }

        @Override
        public void suspend() {
        }

        @Override
        public void restore() {
        }

        @Override
        public void persist(List<String> lines, java.util.function.Consumer<List<String>> persistedOutput) {
        }

        @Override
        public void reset() {
            lines = List.of();
        }
    }
}