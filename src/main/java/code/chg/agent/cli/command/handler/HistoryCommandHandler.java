package code.chg.agent.cli.command.handler;

import code.chg.agent.cli.command.CommandResult;
import code.chg.agent.cli.command.ParsedCommand;
import code.chg.agent.cli.command.SessionCommandContext;
import code.chg.agent.cli.command.SessionCommandHandler;
import code.chg.agent.core.session.HistoryMessageData;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title HistoryCommandHandler
 * @description Provides the HistoryCommandHandler implementation.
 */
public class HistoryCommandHandler implements SessionCommandHandler {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    @Override
    public boolean supports(ParsedCommand command) {
        return "history".equals(command.name());
    }

    @Override
    public CommandResult handle(ParsedCommand command, SessionCommandContext context) {
        int page = 1;
        int limit = 10;
        if (!command.arguments().isEmpty()) {
            String first = command.arguments().getFirst();
            if (first.contains(",")) {
                String[] parts = first.split(",", 2);
                page = Integer.parseInt(parts[0]);
                limit = Integer.parseInt(parts[1]);
            } else {
                limit = Integer.parseInt(first);
            }
        }
        List<HistoryMessageData> history = context.agent().history(context.agent().currentSession().getSessionId(), page, limit);
        StringBuilder output = new StringBuilder();
        int index = 1;
        for (HistoryMessageData item : history) {
            output.append("[").append(index++).append("] ")
                    .append(item.getRole()).append("  ")
                    .append(FORMATTER.format(Instant.ofEpochMilli(item.getCreatedAt()))).append("  ")
                    .append(item.getContent() == null ? "" : item.getContent())
                    .append(System.lineSeparator());
        }
        return CommandResult.handled(output.toString().stripTrailing());
    }
}