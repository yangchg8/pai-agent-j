package code.chg.agent.cli.command;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ParsedCommand
 * @description Defines the ParsedCommand record.
 */
public record ParsedCommand(String name, List<String> arguments, String rawInput) {
}