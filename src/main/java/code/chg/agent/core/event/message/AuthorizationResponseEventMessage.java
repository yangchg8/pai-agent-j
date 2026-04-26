package code.chg.agent.core.event.message;

import code.chg.agent.core.event.EventMessage;
import code.chg.agent.core.event.EventMessageType;
import code.chg.agent.core.event.body.EventBody;
import code.chg.agent.core.event.body.AuthorizationResponseEventBody;
import code.chg.agent.utils.MessageIdGenerator;
import lombok.Getter;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title AuthorizationResponseEventMessage
 * @description Event message carrying the user's authorization decision.
 */
@Getter
public class AuthorizationResponseEventMessage implements EventMessage {
    private final AuthorizationResponseEventBody body;
    private final String id;

    public AuthorizationResponseEventMessage(AuthorizationResponseEventBody body) {
        this.body = body;
        this.id = MessageIdGenerator.generateWithPrefix("auth_resp");

    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public EventMessageType type() {
        return EventMessageType.TOOL_AUTHORIZATION_RESPONSE;
    }

    @Override
    public EventBody body() {
        return body;
    }
}
