package code.chg.agent.cli;

import code.chg.agent.cli.command.CliCommandCompleter;
import code.chg.agent.cli.command.CommandDispatcher;
import code.chg.agent.cli.command.CommandResult;
import code.chg.agent.cli.command.SessionCommandContext;
import code.chg.agent.cli.render.LessRenderer;
import code.chg.agent.core.event.body.AuthorizationResponseEventBody;
import code.chg.agent.core.event.body.AuthorizationScope;
import code.chg.agent.llm.component.AuthorizationRequirementContent;
import code.chg.agent.lib.agent.PaiAgent;
import org.jline.keymap.KeyMap;
import org.jline.reader.EndOfFileException;
import org.jline.reader.Binding;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.Widget;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title CLI
 * @description Terminal CLI for PaiAgent.
 */
public class CLI {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_DIM = "\u001B[2m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_YELLOW = "\u001B[33m";

    public static void main(String[] args) throws Exception {
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        PaiAgent agent = new PaiAgent(System.getProperty("user.dir"));
        interactiveLoop(agent, terminal);
    }

    private static void interactiveLoop(PaiAgent agent, Terminal terminal) {
        CommandDispatcher commandDispatcher = CommandDispatcher.defaultDispatcher();
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new CliCommandCompleter(agent))
                .variable(LineReader.HISTORY_FILE,
                        System.getProperty("user.home") + "/.pai_agent_history")
                .build();
        reader.option(LineReader.Option.AUTO_LIST, true);
        reader.option(LineReader.Option.AUTO_MENU_LIST, true);
        configureMultilineInput(reader);


        LessRenderer renderer = new LessRenderer(terminal);
        printBanner(terminal);

        while (true) {
            String line;
            try {
                line = reader.readLine(buildPrompt(agent).toAnsi());
            } catch (UserInterruptException e) {
                break; // Ctrl-C
            } catch (EndOfFileException e) {
                break; // Ctrl-D
            }

            if (line == null)
                break;
            line = line.strip();
            if (line.isEmpty())
                continue;
            if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line))
                break;

            CommandResult commandResult = commandDispatcher.dispatch(line, new SessionCommandContext(agent, renderer));
            if (commandResult.handled()) {
                if (commandResult.output() != null && !commandResult.output().isBlank()) {
                    terminal.writer().println();
                    terminal.writer().println(commandResult.output());
                }
                printPendingSystemNotices(agent, terminal);
                terminal.writer().println();
                terminal.writer().flush();
                continue;
            }

            terminal.writer().println();
            terminal.writer().flush();
            renderer.markRenderAnchor();
            agent.talk(line, renderer);
            processAuthorizationLoop(agent, terminal, reader, renderer);
            renderer.persistCurrentView();
            printPendingSystemNotices(agent, terminal);

            renderer.clear();
            terminal.writer().println();
            terminal.writer().flush();
        }

        terminal.writer().println();
        terminal.writer().println("Bye!");
        terminal.writer().flush();
    }

    private static void printBanner(Terminal terminal) {
        terminal.writer().println();
        terminal.writer().println(style(ANSI_BOLD + ANSI_BLUE, "PAI Agent")
                + style(ANSI_DIM, "  terminal workspace"));
        terminal.writer().println(style(ANSI_DIM,
                "Enter submits. Ctrl+Enter inserts a newline when your terminal sends it. Ctrl+J is the fallback newline shortcut. Use exit to quit."));
        terminal.writer().flush();
    }

    private static AttributedString buildPrompt(PaiAgent agent) {
        String promptText = agent.statusLine() + System.lineSeparator() + "pai> ";
        return new AttributedString(promptText,
                AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.CYAN));
    }

    private static void printPendingSystemNotices(PaiAgent agent, Terminal terminal) {
        List<String> notices = agent.drainPendingSystemNotices();
        if (notices.isEmpty()) {
            return;
        }
        for (String notice : notices) {
            terminal.writer().println(style(ANSI_DIM, "[SYSTEM] ") + notice);
        }
        terminal.writer().flush();
    }

    private static void configureMultilineInput(LineReader reader) {
        if (reader == null) {
            return;
        }
        Widget insertNewline = () -> {
            reader.getBuffer().write("\n");
            reader.callWidget(LineReader.REDRAW_LINE);
            return true;
        };
        Widget insertSlashAndShowCommands = () -> {
            reader.getBuffer().write("/");
            if ("/".equals(reader.getBuffer().toString())) {
                reader.callWidget(LineReader.LIST_CHOICES);
            } else {
                reader.callWidget(LineReader.REDRAW_LINE);
            }
            return true;
        };
        bindWidget(reader, insertNewline, LineReader.MAIN, multilineKeySequences());
        bindWidget(reader, insertNewline, LineReader.EMACS, multilineKeySequences());
        bindWidget(reader, insertNewline, LineReader.VIINS, multilineKeySequences());
        bindWidget(reader, insertSlashAndShowCommands, LineReader.MAIN, "/");
        bindWidget(reader, insertSlashAndShowCommands, LineReader.EMACS, "/");
        bindWidget(reader, insertSlashAndShowCommands, LineReader.VIINS, "/");
        reader.variable(LineReader.SECONDARY_PROMPT_PATTERN, style(ANSI_DIM, "... "));
    }

    private static void bindWidget(LineReader reader, Widget widget, String keyMapName, String... sequences) {
        if (reader == null || keyMapName == null || sequences == null) {
            return;
        }
        KeyMap<Binding> keyMap = reader.getKeyMaps().get(keyMapName);
        if (keyMap == null) {
            return;
        }
        keyMap.bind(widget, sequences);
    }

    private static String[] multilineKeySequences() {
        return new String[]{
                KeyMap.ctrl('J'),
                KeyMap.alt("\r"),
                "\u001B[13;5u",
                "\u001B[27;5;13~",
                "\u001B[13;9u",
                "\u001B[27;9;13~",
                "\u001B[13;13u",
                "\u001B[27;13;13~"
        };
    }

    private static void processAuthorizationLoop(PaiAgent agent, Terminal terminal, LineReader reader, LessRenderer renderer) {
        Set<String> handledRequestIds = new HashSet<>();
        while (true) {
            List<LessRenderer.AuthorizationRequestView> pendingRequests = nextAuthorizationRequests(renderer, handledRequestIds);
            if (pendingRequests.isEmpty()) {
                return;
            }

            List<AuthorizationResponseEventBody> responses = new ArrayList<>();
            for (LessRenderer.AuthorizationRequestView request : pendingRequests) {
                renderer.persistCurrentView();
                AuthorizationScope scope = promptAuthorization(reader, terminal, request);
                responses.add(new AuthorizationResponseEventBody(request.id(), scope));
                handledRequestIds.add(request.id());
            }

            renderer.markRenderAnchor();
            for (AuthorizationResponseEventBody response : responses) {
                agent.authorize(response, renderer);
            }
        }
    }

    private static List<LessRenderer.AuthorizationRequestView> nextAuthorizationRequests(
            LessRenderer renderer,
            Set<String> handledRequestIds) {
        return renderer.authorizationRequests().stream()
                .filter(request -> request.id() != null && !handledRequestIds.contains(request.id()))
                .toList();
    }

    private static AuthorizationScope promptAuthorization(
            LineReader reader,
            Terminal terminal,
            LessRenderer.AuthorizationRequestView request) {
        terminal.writer().println();
        terminal.writer().println(style(ANSI_BOLD + ANSI_YELLOW, "Authorization required"));
        terminal.writer().println(style(ANSI_DIM, "Tool") + "       " + valueOrDash(request.toolName()));
        terminal.writer().println(style(ANSI_DIM, "Call ID") + "    " + valueOrDash(request.toolCallId()));
        terminal.writer().println(style(ANSI_DIM, "Reason") + "     " + valueOrEmpty(request.prompt()));
        printAuthorizationItems(terminal, request.content());
        terminal.writer().println(style(ANSI_DIM,
                request.allowSave()
                        ? "Choices: y allow once / s allow and save / n reject"
                        : "Choices: y allow / n reject"));
        terminal.writer().flush();

        String choices = request.allowSave() ? "y once / s save / n reject" : "y allow / n reject";
        while (true) {
            String line;
            try {
                terminal.writer().flush();
                line = reader.readLine(style(ANSI_CYAN, "auth> ") + style(ANSI_DIM, "[" + choices + "] "));
            } catch (UserInterruptException | EndOfFileException exception) {
                terminal.writer().println();
                terminal.writer().flush();
                return AuthorizationScope.REJECTED;
            }
            Character choice = firstAuthorizationChoice(line, request.allowSave());
            if (choice == null) {
                continue;
            }
            return switch (choice) {
                case 'y' -> request.allowSave() ? AuthorizationScope.ONCE : AuthorizationScope.CONTINUE;
                case 's' -> AuthorizationScope.GRANT;
                default -> AuthorizationScope.REJECTED;
            };
        }
    }

    private static void printAuthorizationItems(Terminal terminal, AuthorizationRequirementContent content) {
        if (content == null || content.getItems() == null || content.getItems().isEmpty()) {
            return;
        }
        terminal.writer().println(style(ANSI_DIM, "Permissions"));
        for (AuthorizationRequirementContent.AuthorizationRequirementItem item : content.getItems()) {
            terminal.writer().println("- " + valueOrDash(item.getResource()) + "  " + item.getPermissions());
        }
    }

    private static String style(String ansi, String content) {
        return ansi + content + ANSI_RESET;
    }

    private static Character firstAuthorizationChoice(String line, boolean allowSave) {
        if (line == null) {
            return null;
        }
        for (int index = 0; index < line.length(); index++) {
            Character choice = normalizeAuthorizationChoice(line.charAt(index), allowSave);
            if (choice != null) {
                return choice;
            }
        }
        return null;
    }

    private static Character normalizeAuthorizationChoice(char raw, boolean allowSave) {
        char normalized = Character.toLowerCase(raw);
        if (normalized == 'y' || normalized == 'n') {
            return normalized;
        }
        if (allowSave && normalized == 's') {
            return normalized;
        }
        return null;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

}
