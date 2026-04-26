package code.chg.agent.cli.command.handler;

import code.chg.agent.cli.command.CommandResult;
import code.chg.agent.cli.command.ParsedCommand;
import code.chg.agent.cli.command.SessionCommandContext;
import code.chg.agent.cli.command.SessionCommandHandler;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title McpLoadCommandHandler
 * @description Provides the McpLoadCommandHandler implementation.
 */
public class McpLoadCommandHandler implements SessionCommandHandler {
    @Override
    public boolean supports(ParsedCommand command) {
        return "mcp load".equals(command.name());
    }

    @Override
    public CommandResult handle(ParsedCommand command, SessionCommandContext context) {
        if (command.arguments().size() < 2) {
            return CommandResult.handled("Usage: /mcp load {mcpName}");
        }
        String mcpName = command.arguments().get(1);
        context.agent().loadMcp(mcpName);
        return CommandResult.handled("Loaded MCP: " + mcpName);
    }
}