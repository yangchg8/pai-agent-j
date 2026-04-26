package code.chg.agent.cli.command.handler;

import code.chg.agent.cli.command.CommandResult;
import code.chg.agent.cli.command.ParsedCommand;
import code.chg.agent.cli.command.SessionCommandContext;
import code.chg.agent.cli.command.SessionCommandHandler;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LoadSessionCommandHandler
 * @description Provides the LoadSessionCommandHandler implementation.
 */
public class LoadSessionCommandHandler implements SessionCommandHandler {
    @Override
    public boolean supports(ParsedCommand command) {
        return "load".equals(command.name());
    }

    @Override
    public CommandResult handle(ParsedCommand command, SessionCommandContext context) {
        if (command.arguments().isEmpty()) {
            return CommandResult.handled("Usage: /load {sessionId}");
        }
        String sessionId = command.arguments().getFirst();
        context.agent().loadSession(sessionId);
        return CommandResult.handled("Loaded session: " + sessionId);
    }
}