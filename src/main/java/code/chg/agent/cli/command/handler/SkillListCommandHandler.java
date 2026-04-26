package code.chg.agent.cli.command.handler;

import code.chg.agent.cli.command.CommandResult;
import code.chg.agent.cli.command.ParsedCommand;
import code.chg.agent.cli.command.SessionCommandContext;
import code.chg.agent.cli.command.SessionCommandHandler;
import code.chg.agent.lib.skill.SkillMetadata;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SkillListCommandHandler
 * @description Provides the SkillListCommandHandler implementation.
 */
public class SkillListCommandHandler implements SessionCommandHandler {
    @Override
    public boolean supports(ParsedCommand command) {
        return "skill list".equals(command.name());
    }

    @Override
    public CommandResult handle(ParsedCommand command, SessionCommandContext context) {
        StringBuilder output = new StringBuilder();
        for (SkillMetadata skill : context.agent().skills()) {
            output.append(skill.name())
                    .append(" | ")
                    .append(skill.description() == null ? "" : skill.description())
                    .append(" | ")
                    .append(skill.pathToSkillMd())
                    .append(System.lineSeparator());
        }
        return CommandResult.handled(output.toString().stripTrailing());
    }
}