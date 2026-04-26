package code.chg.agent.core.tool;

import code.chg.agent.llm.component.AuthorizationRequirementContent;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolRuleBasedAuthorizationMode
 * @description Authorization mode that carries directly computed permission requirements.
 */
public class ToolRuleBasedAuthorizationMode implements ToolAuthorizationMode {
    private final AuthorizationRequirementContent content;

    public ToolRuleBasedAuthorizationMode(AuthorizationRequirementContent content) {
        this.content = content;
    }

    public AuthorizationRequirementContent authorizationContent() {
        return content;
    }

    public ToolAuthModeType getType() {
        return ToolAuthModeType.RULE_BASED;
    }
}
