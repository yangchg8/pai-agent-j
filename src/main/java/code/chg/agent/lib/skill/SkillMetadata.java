package code.chg.agent.lib.skill;

import java.nio.file.Path;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SkillMetadata
 * @description Defines the SkillMetadata record.
 */
public record SkillMetadata(String name, String description, Path pathToSkillMd) {
}