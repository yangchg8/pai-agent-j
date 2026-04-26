package code.chg.agent.cli.command.handler;

import code.chg.agent.cli.command.CommandResult;
import code.chg.agent.cli.command.ParsedCommand;
import code.chg.agent.cli.command.SessionCommandContext;
import code.chg.agent.cli.command.SessionCommandHandler;
import org.jline.utils.InfoCmp;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ClearCommandHandler
 * @description Provides the ClearCommandHandler implementation.
 */
public class ClearCommandHandler implements SessionCommandHandler {
    @Override
    public boolean supports(ParsedCommand command) {
        return "clear".equals(command.name());
    }

    @Override
    public CommandResult handle(ParsedCommand command, SessionCommandContext context) {
        if (context.renderer() == null || context.renderer().terminal() == null) {
            return CommandResult.handled(null);
        }
        context.renderer().clear();
        context.renderer().terminal().puts(InfoCmp.Capability.clear_screen);
        context.renderer().terminal().flush();
        return CommandResult.handled(null);
    }
}