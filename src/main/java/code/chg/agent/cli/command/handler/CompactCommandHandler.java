package code.chg.agent.cli.command.handler;

import code.chg.agent.cli.command.CommandResult;
import code.chg.agent.cli.command.ParsedCommand;
import code.chg.agent.cli.command.SessionCommandContext;
import code.chg.agent.cli.command.SessionCommandHandler;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title CompactCommandHandler
 * @description Provides the CompactCommandHandler implementation.
 */
public class CompactCommandHandler implements SessionCommandHandler {
    @Override
    public boolean supports(ParsedCommand command) {
        return "compact".equals(command.name());
    }

    @Override
    public CommandResult handle(ParsedCommand command, SessionCommandContext context) {
        if (context.renderer() != null && context.renderer().terminal() != null) {
            context.renderer().terminal().writer().println();
            context.renderer().terminal().writer().println("[SYSTEM] Compacting conversation context...");
            context.renderer().terminal().writer().flush();
            context.renderer().markRenderAnchor();
        }
        context.agent().compact(context.renderer());
        if (context.renderer() != null) {
            context.renderer().persistCurrentView();
            context.renderer().clear();
        }
        return CommandResult.handled("Compaction completed.");
    }
}