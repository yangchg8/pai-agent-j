package code.chg.agent.lib.skill;

import code.chg.agent.lib.prompt.SkillPrompts;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SkillRenderer
 * @description Provides the SkillRenderer implementation.
 */
public final class SkillRenderer {
    private SkillRenderer() {
    }

    /**
     * Renders the available skills as a compact system message block.
     * Returns {@code null} when there are no skills to show.
     */
    public static String renderSkillsSection(List<SkillMetadata> skills) {
        if (skills == null || skills.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Skills\n");
        sb.append(SkillPrompts.SKILLS_INTRO_WITH_ABSOLUTE_PATHS).append('\n');
        sb.append("### Available skills\n");
        for (SkillMetadata skill : skills) {
            sb.append("- **").append(skill.name()).append("** – ")
                    .append(skill.description().isEmpty() ? "(no description)" : skill.description())
                    .append(" (file: `").append(skill.pathToSkillMd().toString().replace('\\', '/'))
                    .append("`)");
            sb.append('\n');
        }
        sb.append("### How to use skills\n");
        sb.append(SkillPrompts.SKILLS_HOW_TO_USE_WITH_ABSOLUTE_PATHS).append('\n');
        return sb.toString();
    }
}