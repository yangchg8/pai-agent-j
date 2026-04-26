package code.chg.agent.cli.command;

import code.chg.agent.cli.command.handler.CompactCommandHandler;
import code.chg.agent.cli.command.handler.HistoryCommandHandler;
import code.chg.agent.cli.command.handler.LoadSessionCommandHandler;
import code.chg.agent.cli.command.handler.McpListCommandHandler;
import code.chg.agent.cli.command.handler.McpLoadCommandHandler;
import code.chg.agent.cli.command.handler.ResetCommandHandler;
import code.chg.agent.cli.command.handler.SessionListCommandHandler;
import code.chg.agent.cli.command.handler.SkillListCommandHandler;
import code.chg.agent.cli.command.handler.StatusCommandHandler;
import code.chg.agent.cli.command.handler.ClearCommandHandler;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title CommandDispatcher
 * @description Provides the CommandDispatcher implementation.
 */
public class CommandDispatcher {
    private final CommandParser parser = new CommandParser();
    private final List<SessionCommandHandler> handlers;

    public CommandDispatcher(List<SessionCommandHandler> handlers) {
        this.handlers = handlers;
    }

    public static CommandDispatcher defaultDispatcher() {
        return new CommandDispatcher(List.of(
                new HistoryCommandHandler(),
                new LoadSessionCommandHandler(),
                new SkillListCommandHandler(),
                new McpListCommandHandler(),
                new McpLoadCommandHandler(),
                new StatusCommandHandler(),
                new CompactCommandHandler(),
                new SessionListCommandHandler(),
                new ResetCommandHandler(),
                new ClearCommandHandler()
        ));
    }

    public CommandResult dispatch(String input, SessionCommandContext context) {
        ParsedCommand command = parser.parse(input);
        if (command == null) {
            return CommandResult.notHandled();
        }
        for (SessionCommandHandler handler : handlers) {
            if (handler.supports(command)) {
                return handler.handle(command, context);
            }
        }
        return CommandResult.handled("Unknown command: " + input);
    }
}