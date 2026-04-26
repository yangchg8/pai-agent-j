package code.chg.agent.core.event.body;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title AuthorizationResponseEventBody
 * @description Authorization response payload containing the request ID and granted scope.
 */
public record AuthorizationResponseEventBody(String id, AuthorizationScope scope) implements EventBody {

    public String authorizationId() {
        return id;
    }

}
