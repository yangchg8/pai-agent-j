package code.chg.agent.llm.component;

import code.chg.agent.core.permission.Permission;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title AuthorizationRequirementContent
 * @description Structured authorization request content shown to the user.
 */
@Data
@NoArgsConstructor
public class AuthorizationRequirementContent {

    private String tips;

    private List<AuthorizationRequirementItem> items;


    @Data
    @NoArgsConstructor
    public static class AuthorizationRequirementItem {
        /**
         * Resource path requiring authorization.
         */
        private String resource;

        /**
         * Permissions requested for the resource.
         */
        private List<Permission> permissions;
    }
}
