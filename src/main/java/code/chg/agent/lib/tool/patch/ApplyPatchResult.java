package code.chg.agent.lib.tool.patch;

import lombok.Data;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ApplyPatchResult
 * @description Result returned by ApplyPatchTool.
 */
@Data
public class ApplyPatchResult {

    private boolean success;
    private String message;

    private ApplyPatchResult() {
    }

    public static ApplyPatchResult ok(String message) {
        ApplyPatchResult r = new ApplyPatchResult();
        r.success = true;
        r.message = message;
        return r;
    }

    public static ApplyPatchResult error(String message) {
        ApplyPatchResult r = new ApplyPatchResult();
        r.success = false;
        r.message = message;
        return r;
    }

    @Override
    public String toString() {
        return message;
    }
}
