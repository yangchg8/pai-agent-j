package code.chg.agent.core.permission;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title Permission
 * @description Enumerates supported permission levels.
 */
public enum Permission {
    /**
     * No permission granted.
     */
    NONE,
    /**
     * Read permission.
     */
    READ,
    /**
     * Write permission.
     */
    WRITE,
    /**
     * Execute permission.
     */
    EXECUTE,
    /**
     * Full permission across all supported operations.
     */
    ALL;
}
