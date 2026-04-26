package code.chg.agent.cli.command;

import java.util.Arrays;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title CommandParser
 * @description Provides the CommandParser implementation.
 */
public class CommandParser {
    public ParsedCommand parse(String input) {
        if (input == null || input.isBlank() || !input.startsWith("/")) {
            return null;
        }
        String normalized = input.substring(1).trim();
        if (normalized.isBlank()) {
            return null;
        }
        List<String> parts = Arrays.stream(normalized.split("\\s+"))
                .filter(part -> !part.isBlank())
                .toList();
        if (parts.isEmpty()) {
            return null;
        }
        String name = parts.size() >= 2
                && "session".equalsIgnoreCase(parts.getFirst())
                && "list".equalsIgnoreCase(parts.get(1))
                ? "session list"
                : parts.size() >= 2
                && "skill".equalsIgnoreCase(parts.getFirst())
                && "list".equalsIgnoreCase(parts.get(1))
                ? "skill list"
                : parts.size() >= 2
                && "mcp".equalsIgnoreCase(parts.getFirst())
                ? "mcp " + parts.get(1).toLowerCase()
                : parts.getFirst().toLowerCase();
        return new ParsedCommand(name, parts.size() > 1 ? parts.subList(1, parts.size()) : List.of(), input);
    }
}