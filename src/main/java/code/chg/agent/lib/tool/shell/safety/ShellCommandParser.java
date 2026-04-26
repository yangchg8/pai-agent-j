package code.chg.agent.lib.tool.shell.safety;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ShellCommandParser
 * @description Conservative shell command parser used by the safety analyzer.
 */
public final class ShellCommandParser {

    private ShellCommandParser() {
    }

    public static ParsedShell tryParseShell(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return new ParsedShell(source);
    }

    public static List<List<String>> tryParseWordOnlyCommandsSequence(ParsedShell shell, String src) {
        if (shell == null || src == null || !shell.source().equals(src)) {
            return null;
        }
        return parsePlainCommandSequence(src);
    }

    public static List<List<String>> parseShellLcPlainCommands(List<String> command) {
        String script = CommandUtils.extractBashLcScript(command);
        if (script == null) {
            return null;
        }
        ParsedShell shell = tryParseShell(script);
        if (shell == null) {
            return null;
        }
        return tryParseWordOnlyCommandsSequence(shell, script);
    }

    static List<List<String>> parsePlainCommandSequence(String source) {
        List<List<String>> commands = new ArrayList<>();
        List<String> currentCommand = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean justSawSeparator = true;

        for (int index = 0; index < source.length(); index++) {
            char ch = source.charAt(index);

            if (inSingleQuote) {
                if (ch == '\'') {
                    inSingleQuote = false;
                } else {
                    currentWord.append(ch);
                }
                continue;
            }

            if (inDoubleQuote) {
                if (ch == '"') {
                    inDoubleQuote = false;
                } else if (ch == '$' || ch == '`' || ch == '\\') {
                    return null;
                } else {
                    currentWord.append(ch);
                }
                continue;
            }

            if (Character.isWhitespace(ch)) {
                if (ch == '\n' || ch == '\r') {
                    if (!flushWord(currentWord, currentCommand)) {
                        return null;
                    }
                    if (!finishCommand(currentCommand, commands, justSawSeparator)) {
                        return null;
                    }
                    justSawSeparator = true;
                } else {
                    if (!flushWord(currentWord, currentCommand)) {
                        return null;
                    }
                    justSawSeparator = false;
                }
                continue;
            }

            switch (ch) {
                case '\'' -> inSingleQuote = true;
                case '"' -> inDoubleQuote = true;
                case '&' -> {
                    if (index + 1 >= source.length() || source.charAt(index + 1) != '&') {
                        return null;
                    }
                    if (!flushWord(currentWord, currentCommand) || !finishCommand(currentCommand, commands, justSawSeparator)) {
                        return null;
                    }
                    justSawSeparator = true;
                    index++;
                }
                case '|' -> {
                    if (index + 1 < source.length() && source.charAt(index + 1) == '|') {
                        if (!flushWord(currentWord, currentCommand) || !finishCommand(currentCommand, commands, justSawSeparator)) {
                            return null;
                        }
                        justSawSeparator = true;
                        index++;
                    } else {
                        if (!flushWord(currentWord, currentCommand) || !finishCommand(currentCommand, commands, justSawSeparator)) {
                            return null;
                        }
                        justSawSeparator = true;
                    }
                }
                case ';' -> {
                    if (!flushWord(currentWord, currentCommand) || !finishCommand(currentCommand, commands, justSawSeparator)) {
                        return null;
                    }
                    justSawSeparator = true;
                }
                case '$', '`', '>', '<', '(', ')', '{', '}', '[', ']', '\\' -> {
                    return null;
                }
                default -> {
                    currentWord.append(ch);
                    justSawSeparator = false;
                }
            }
        }

        if (inSingleQuote || inDoubleQuote) {
            return null;
        }
        if (!flushWord(currentWord, currentCommand)) {
            return null;
        }
        if (!finishCommand(currentCommand, commands, justSawSeparator)) {
            return null;
        }
        return commands.isEmpty() ? null : commands;
    }

    private static boolean flushWord(StringBuilder currentWord, List<String> currentCommand) {
        if (currentWord.isEmpty()) {
            return true;
        }
        String word = currentWord.toString();
        currentWord.setLength(0);
        if (word.isEmpty()) {
            return false;
        }
        currentCommand.add(word);
        return true;
    }

    private static boolean finishCommand(List<String> currentCommand, List<List<String>> commands, boolean justSawSeparator) {
        if (currentCommand.isEmpty()) {
            return justSawSeparator && commands.isEmpty();
        }
        if (looksLikeVariableAssignmentPrefix(currentCommand.getFirst())) {
            return false;
        }
        commands.add(List.copyOf(currentCommand));
        currentCommand.clear();
        return true;
    }

    private static boolean looksLikeVariableAssignmentPrefix(String token) {
        int eq = token.indexOf('=');
        if (eq <= 0) {
            return false;
        }
        if (!Character.isLetter(token.charAt(0)) && token.charAt(0) != '_') {
            return false;
        }
        for (int i = 1; i < eq; i++) {
            char ch = token.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return false;
            }
        }
        return true;
    }

    public record ParsedShell(String source) {
    }
}
