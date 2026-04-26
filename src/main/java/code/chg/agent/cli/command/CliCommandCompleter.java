package code.chg.agent.cli.command;

import code.chg.agent.lib.agent.PaiAgent;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title CliCommandCompleter
 * @description Provides the CliCommandCompleter implementation.
 */
public class CliCommandCompleter implements Completer {
    private final PaiAgent agent;

    public CliCommandCompleter(PaiAgent agent) {
        this.agent = agent;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String input = line.line();
        if (input == null || !input.startsWith("/")) {
            return;
        }
        if ("/".equals(input)) {
            baseCommands().forEach(command -> candidates.add(new Candidate(command)));
            return;
        }
        if (input.startsWith("/load ")) {
            agent.listSessions(1, 100).forEach(session -> candidates.add(new Candidate(session.getSessionId())));
            return;
        }
        if (input.startsWith("/mcp load ")) {
            agent.listMcpServers().stream()
                    .filter(view -> !view.loaded())
                    .forEach(view -> candidates.add(new Candidate(view.name())));
            return;
        }
        if (input.startsWith("/mcp unload ")) {
            agent.listMcpServers().stream()
                    .filter(PaiAgent.McpServerView::loaded)
                    .forEach(view -> candidates.add(new Candidate(view.name())));
            return;
        }
        if (input.startsWith("/skill list")) {
            return;
        }
        for (String command : baseCommands()) {
            if (command.startsWith(input)) {
                candidates.add(new Candidate(command));
            }
        }
    }

    private List<String> baseCommands() {
        return List.of(
                "/history",
                "/load",
                "/skill list",
                "/mcp list",
                "/mcp load",
                "/mcp unload",
                "/status",
                "/compact",
                "/session list",
                "/reset",
                "/clear"
        );
    }
}