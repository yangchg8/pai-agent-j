package code.chg.agent.cli.command.handler;

import code.chg.agent.cli.command.CommandResult;
import code.chg.agent.cli.command.ParsedCommand;
import code.chg.agent.cli.command.SessionCommandContext;
import code.chg.agent.cli.command.SessionCommandHandler;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ResetCommandHandler
 * @description Provides the ResetCommandHandler implementation.
 */
public class ResetCommandHandler implements SessionCommandHandler {
    @Override
    public boolean supports(ParsedCommand command) {
        return "reset".equals(command.name());
    }

    @Override
    public CommandResult handle(ParsedCommand command, SessionCommandContext context) {
        String previousSessionId = context.agent().currentSession().getSessionId();
        String newSessionId = context.agent().resetCurrentSession().getSessionId();
        return CommandResult.handled("Reset session: " + previousSessionId + " -> " + newSessionId);
    }
}