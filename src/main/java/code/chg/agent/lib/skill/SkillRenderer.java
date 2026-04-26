package code.chg.agent.lib.skill;

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
        sb.append("## Available Skills\n");
        sb.append("The following skills are available. Each entry shows: name – description (file path).\n");
        sb.append("Use `read_file` on the listed path to load a skill's full instructions when needed.\n\n");
        for (SkillMetadata skill : skills) {
            sb.append("- **").append(skill.name()).append("** – ")
                    .append(skill.description().isEmpty() ? "(no description)" : skill.description())
                    .append(" (`").append(skill.pathToSkillMd().toString().replace('\\', '/'))
                    .append("`)");
            sb.append('\n');
        }
        return sb.toString();
    }
}