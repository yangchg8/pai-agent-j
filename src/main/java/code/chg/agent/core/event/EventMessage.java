package code.chg.agent.core.event;

import code.chg.agent.core.event.body.EventBody;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title EventMessage
 * @description Event message abstraction carrying the message body and type.
 */
public interface EventMessage {

    String id();

    EventMessageType type();

    EventBody body();
}
