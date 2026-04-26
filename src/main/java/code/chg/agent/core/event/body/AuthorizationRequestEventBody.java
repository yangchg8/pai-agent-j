package code.chg.agent.core.event.body;

import code.chg.agent.llm.component.AuthorizationRequirementContent;
import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title AuthorizationRequestEventBody
 * @description Event payload describing the authorization content required for a tool call.
 */
@Data
public class AuthorizationRequestEventBody implements EventBody {
    private final String toolCallId;
    private final String authorizationPrompt;
    private final AuthorizationRequirementContent directContent;

    /**
     * LLM-based authorization: carries a prompt string for LLM analysis.
     */
    public AuthorizationRequestEventBody(String toolCallId, String permissionPrompt) {
        this.toolCallId = toolCallId;
        this.authorizationPrompt = permissionPrompt;
        this.directContent = null;
    }

    /**
     * Rule-based authorization: carries pre-built structured content, Brain skips LLM.
     */
    public AuthorizationRequestEventBody(String toolCallId, AuthorizationRequirementContent directContent) {
        this.toolCallId = toolCallId;
        this.authorizationPrompt = null;
        this.directContent = directContent;
    }

    public boolean hasDirectContent() {
        return directContent != null;
    }
}
