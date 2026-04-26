package code.chg.agent.cli.command.handler;

import code.chg.agent.cli.command.CommandResult;
import code.chg.agent.cli.command.ParsedCommand;
import code.chg.agent.cli.command.SessionCommandContext;
import code.chg.agent.cli.command.SessionCommandHandler;
import code.chg.agent.core.session.SessionSummaryData;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SessionListCommandHandler
 * @description Provides the SessionListCommandHandler implementation.
 */
public class SessionListCommandHandler implements SessionCommandHandler {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    @Override
    public boolean supports(ParsedCommand command) {
        return "session list".equals(command.name());
    }

    @Override
    public CommandResult handle(ParsedCommand command, SessionCommandContext context) {
        StringBuilder output = new StringBuilder();
        for (SessionSummaryData session : context.agent().listSessions(1, 50)) {
            String title = displayTitle(session);
            output.append(session.getSessionId())
                    .append(" | ")
                    .append(title)
                    .append(" | ")
                    .append(FORMATTER.format(Instant.ofEpochMilli(session.getLastActiveAt())))
                    .append(" | token=")
                    .append(session.getLatestTokenCount())
                    .append(System.lineSeparator());
        }
        return CommandResult.handled(output.toString().stripTrailing());
    }

    private String displayTitle(SessionSummaryData session) {
        if (session == null) {
            return "Untitled Session";
        }
        String title = session.getTitle();
        if (title != null && !title.isBlank() && !title.equals(session.getSessionId())) {
            return title;
        }
        String latestUserMessage = session.getLatestUserMessage();
        if (latestUserMessage != null && !latestUserMessage.isBlank()) {
            String normalized = latestUserMessage.strip();
            return normalized.length() <= 15 ? normalized : normalized.substring(0, 15) + "...";
        }
        return "Untitled Session";
    }
}