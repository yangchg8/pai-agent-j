package code.chg.agent.cli.command.handler;

import code.chg.agent.cli.command.CommandResult;
import code.chg.agent.cli.command.ParsedCommand;
import code.chg.agent.cli.command.SessionCommandContext;
import code.chg.agent.cli.command.SessionCommandHandler;
import code.chg.agent.core.event.SubscriptionDescriptor;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title StatusCommandHandler
 * @description Provides the StatusCommandHandler implementation.
 */
public class StatusCommandHandler implements SessionCommandHandler {
    @Override
    public boolean supports(ParsedCommand command) {
        return "status".equals(command.name());
    }

    @Override
    public CommandResult handle(ParsedCommand command, SessionCommandContext context) {
        StringBuilder output = new StringBuilder();
        output.append(context.agent().statusLine()).append(System.lineSeparator());
        output.append("Subscribers:").append(System.lineSeparator());
        for (SubscriptionDescriptor descriptor : context.agent().subscriptions()) {
            output.append("- ")
                    .append(descriptor.name())
                    .append(" [")
                    .append("] [")
                    .append(descriptor.type())
                    .append("]")
                    .append(System.lineSeparator());
        }
        return CommandResult.handled(output.toString().stripTrailing());
    }
}