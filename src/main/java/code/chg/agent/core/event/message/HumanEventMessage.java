package code.chg.agent.core.event.message;

import code.chg.agent.core.event.EventMessage;
import code.chg.agent.core.event.EventMessageType;
import code.chg.agent.core.event.body.EventBody;
import code.chg.agent.core.event.body.HumanEventBody;
import code.chg.agent.utils.MessageIdGenerator;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title HumanEventMessage
 * @description Event message that starts a reasoning turn with user input.
 */
public class HumanEventMessage implements EventMessage {

    private final HumanEventBody body;
    private final String id;

    public HumanEventMessage(String content) {
        body = new HumanEventBody(content);
        id = MessageIdGenerator.generateWithPrefix("human");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public EventMessageType type() {
        return EventMessageType.HUMAN_MESSAGE;
    }

    @Override
    public EventBody body() {
        return body;
    }

}
