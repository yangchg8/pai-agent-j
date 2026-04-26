package code.chg.agent.lib.memory;

import code.chg.agent.core.memory.MemoryRegion;
import code.chg.agent.core.tool.Tool;
import code.chg.agent.lib.skill.SkillMetadata;
import code.chg.agent.lib.skill.SkillRenderer;
import code.chg.agent.lib.skill.SkillsManager;
import code.chg.agent.lib.tool.file.FileTool;
import code.chg.agent.utils.ToolUtil;
import code.chg.agent.llm.LLMMessage;
import code.chg.agent.llm.MessageType;
import code.chg.agent.utils.MessageIdGenerator;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SkillMemoryRegion
 * @description Injects the list of available skills into the system context so the LLM
 */
public class SkillMemoryRegion implements MemoryRegion {
    private final SkillsManager skillsManager;

    public SkillMemoryRegion(SkillsManager skillsManager) {
        this.skillsManager = skillsManager;
    }

    @Override
    public String getName() {
        return "SKILL_MEMORY_REGION";
    }

    @Override
    public List<Tool> tools() {
        return ToolUtil.buildTools(FileTool.class, null);
    }

    @Override
    public List<LLMMessage> messages() {
        List<SkillMetadata> skills = skillsManager.skills();
        String content = SkillRenderer.renderSkillsSection(skills);
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String messageId = MessageIdGenerator.generateWithPrefix("skill-memory");
        return List.of(new LLMMessage() {
            @Override
            public String content() {
                return content;
            }

            @Override
            public String id() {
                return messageId;
            }

            @Override
            public MessageType type() {
                return MessageType.SYSTEM;
            }
        });
    }
}
