package code.chg.agent.llm;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LLMRequest
 * @description Request envelope for synchronous or streaming LLM calls.
 */
@Data
public class LLMRequest {
    private List<LLMMessage> messages;
    private List<ToolDefinition> tools;

    public LLMRequest(List<LLMMessage> messages) {
        this(messages, Collections.emptyList());
    }

    public LLMRequest(List<LLMMessage> messages, List<ToolDefinition> tools) {
        this.messages = messages;
        this.tools = tools;
    }
}
