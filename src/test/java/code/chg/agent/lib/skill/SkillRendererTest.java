package code.chg.agent.lib.skill;

import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SkillRendererTest {

    @Test
    public void renderSkillsSectionReturnsNullForEmptyInput() {
        assertEquals(null, SkillRenderer.renderSkillsSection(List.of()));
        assertEquals(null, SkillRenderer.renderSkillsSection(null));
    }

    @Test
    public void renderSkillsSectionIncludesGuidanceAndSkillEntries() {
        String rendered = SkillRenderer.renderSkillsSection(List.of(
                new SkillMetadata("build-helper", "Build workflow helper", Path.of("/tmp/skills/build-helper/SKILL.md")),
                new SkillMetadata("no-desc", "", Path.of("C:\\skills\\no-desc\\SKILL.md"))
        ));

        assertNotNull(rendered);
        assertTrue(rendered.contains("## Skills"));
        assertTrue(rendered.contains("### Available skills"));
        assertTrue(rendered.contains("### How to use skills"));
        assertTrue(rendered.contains("A skill is a set of local instructions to follow that is stored in a `SKILL.md` file."));
        assertTrue(rendered.contains("- **build-helper** – Build workflow helper (`/tmp/skills/build-helper/SKILL.md`)"));
        assertTrue(rendered.contains("- **no-desc** – (no description) (`C:/skills/no-desc/SKILL.md`)"));
        assertTrue(rendered.contains("- Discovery: The list above is the skills available in this session (name + description + file path)."));
        assertTrue(rendered.contains("- How to use a skill (progressive disclosure):"));
    }
}