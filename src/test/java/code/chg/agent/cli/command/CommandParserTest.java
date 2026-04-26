package code.chg.agent.cli.command;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title CommandParserTest
 * @description Provides the CommandParserTest implementation.
 */
public class CommandParserTest {

    @Test
    public void parseReturnsNullForPlainText() {
        CommandParser parser = new CommandParser();
        assertNull(parser.parse("hello world"));
    }

    @Test
    public void parseRecognizesSessionListCommand() {
        CommandParser parser = new CommandParser();
        ParsedCommand command = parser.parse("/session list");

        assertNotNull(command);
        assertEquals("session list", command.name());
    }

    @Test
    public void parseRecognizesMcpLoadCommand() {
        CommandParser parser = new CommandParser();
        ParsedCommand command = parser.parse("/mcp load filesystem");

        assertNotNull(command);
        assertEquals("mcp load", command.name());
        assertEquals("filesystem", command.arguments().get(1));
    }

    @Test
    public void parseRecognizesResetAndClearCommands() {
        CommandParser parser = new CommandParser();

        ParsedCommand reset = parser.parse("/reset");
        ParsedCommand clear = parser.parse("/clear");

        assertNotNull(reset);
        assertEquals("reset", reset.name());
        assertNotNull(clear);
        assertEquals("clear", clear.name());
    }
}