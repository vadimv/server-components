package rsp.page;

import rsp.page.events.SessionEvent;

import java.util.*;
import java.util.function.Consumer;

public final class RedirectableEventsConsumer implements Consumer<SessionEvent> {

    private final Queue<SessionEvent> queue = new LinkedList<>();
    private Consumer<SessionEvent> eventConsumer;

    public RedirectableEventsConsumer() {
        this.eventConsumer = event -> {
            queue.add(event);
        };
    }

    @Override
    public synchronized void accept(SessionEvent event) {
       this.eventConsumer.accept(event);
    }

    public synchronized void redirect(final Consumer<SessionEvent> newCommandsEnqueue) {
        eventConsumer = Objects.requireNonNull(newCommandsEnqueue);
        while (!queue.isEmpty()) {
            final SessionEvent e = queue.remove();
            eventConsumer.accept(e);

        }
    }
}
