package code.chg.agent.cli.command;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SessionCommandHandler
 * @description Defines the SessionCommandHandler contract.
 */
public interface SessionCommandHandler {
    boolean supports(ParsedCommand command);

    CommandResult handle(ParsedCommand command, SessionCommandContext context);
}