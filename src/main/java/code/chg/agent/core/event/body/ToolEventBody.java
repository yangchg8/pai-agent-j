package code.chg.agent.core.event.body;

import code.chg.agent.llm.ToolCall;
import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolEventBody
 * @description Event payload carrying tool execution input or output.
 */
@Data
public class ToolEventBody implements EventBody {
    private final ToolCall toolCall;
    private final String response;

    public ToolEventBody(ToolCall toolCall) {
        this.toolCall = toolCall;
        this.response = null;
    }

    public ToolEventBody(ToolCall toolCall, String toolResponse) {
        this.toolCall = toolCall;
        this.response = toolResponse;
    }

    public String toolCallId() {
        return toolCall.id();
    }

    public String name() {
        return toolCall.name();
    }

    public String arguments() {
        return toolCall.arguments();
    }

    public String response() {
        return response;
    }
}
