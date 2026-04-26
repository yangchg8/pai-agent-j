package code.chg.agent.core.tool;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolRejectedAuthorizationMode
 * @description Defines an authorization mode that rejects tool execution.
 */
public interface ToolRejectedAuthorizationMode extends ToolAuthorizationMode {
    /**
     * Returns the rejection reason.
     *
     * @return the rejection reason
     */
    String rejectReason();

    /**
     * Returns the authorization mode type.
     *
     * @return the rejected authorization mode type
     */
    default ToolAuthModeType getType() {
        return ToolAuthModeType.REJECTED;
    }
}
