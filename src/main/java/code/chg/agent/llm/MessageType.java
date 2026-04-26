package code.chg.agent.llm;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title MessageType
 * @description Enumerates supported persisted LLM message roles.
 */
public enum MessageType {
    /**
     * Assistant message role.
     */
    AI("ai"),
    /**
     * Human message role.
     */
    HUMAN("human"),
    /**
     * System message role.
     */
    SYSTEM("system"),
    /**
     * Tool response role.
     */
    TOOL("tool");

    /**
     * Serialized role name.
     */
    final String type;

    MessageType(String type) {
        this.type = type;
    }
}
