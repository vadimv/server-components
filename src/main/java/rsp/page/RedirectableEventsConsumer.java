package rsp.page;

import rsp.page.events.SessionEvent;

import java.util.*;
import java.util.function.Consumer;

public final class RedirectableEventsConsumer implements Consumer<SessionEvent> {

    private Consumer<SessionEvent> eventConsumer;

    public RedirectableEventsConsumer() {
        this.eventConsumer = event -> {
            throw new IllegalStateException("A session's event loop is not initialized, receiving event: " + event);
        };
    }

    @Override
    public synchronized void accept(SessionEvent event) {
       this.eventConsumer.accept(event);
    }

    public synchronized void redirect(final Consumer<SessionEvent> directRemoteOut) {
        eventConsumer = Objects.requireNonNull(directRemoteOut);
    }
}
