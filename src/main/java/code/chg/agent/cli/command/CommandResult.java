package code.chg.agent.cli.command;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title CommandResult
 * @description Defines the CommandResult record.
 */
public record CommandResult(boolean handled, String output) {
    public static CommandResult notHandled() {
        return new CommandResult(false, null);
    }

    public static CommandResult handled(String output) {
        return new CommandResult(true, output);
    }
}