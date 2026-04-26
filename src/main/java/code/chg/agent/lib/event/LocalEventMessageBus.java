package code.chg.agent.lib.event;

import code.chg.agent.core.event.BaseEventMessageBus;
import code.chg.agent.core.session.SessionStoreManager;
import code.chg.agent.lib.session.LocalSessionStoreManager;

import java.util.concurrent.ExecutorService;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title LocalEventMessageBus
 * @description Local event-bus implementation backed by the on-disk session store.
 */
public class LocalEventMessageBus extends BaseEventMessageBus {
    private static final LocalSessionStoreManager STORE_MANAGER = new LocalSessionStoreManager();

    public LocalEventMessageBus(String sessionId, ExecutorService executorService) {
        super(sessionId,executorService);
    }

    @Override
    public SessionStoreManager getSessionStoreManager() {
        return STORE_MANAGER;
    }
}
