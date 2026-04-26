package code.chg.agent.cli.command.handler;

import code.chg.agent.cli.command.CommandResult;
import code.chg.agent.cli.command.ParsedCommand;
import code.chg.agent.cli.command.SessionCommandContext;
import code.chg.agent.cli.command.SessionCommandHandler;
import code.chg.agent.lib.agent.PaiAgent;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title McpListCommandHandler
 * @description Provides the McpListCommandHandler implementation.
 */
public class McpListCommandHandler implements SessionCommandHandler {
    @Override
    public boolean supports(ParsedCommand command) {
        return "mcp list".equals(command.name());
    }

    @Override
    public CommandResult handle(ParsedCommand command, SessionCommandContext context) {
        StringBuilder output = new StringBuilder();
        for (PaiAgent.McpServerView view : context.agent().listMcpServers()) {
            output.append(view.loaded() ? "[LOADED] " : "[IDLE]   ")
                    .append(view.name())
                    .append("   autoLoad=")
                    .append(view.autoLoad())
                    .append("   transport=")
                    .append(view.transport())
                    .append(System.lineSeparator());
        }
        return CommandResult.handled(output.toString().stripTrailing());
    }
}