package rsp.page;

import rsp.page.events.SessionEvent;

import java.util.*;
import java.util.function.Consumer;

public final class TemporaryBufferedPageCommands implements Consumer<SessionEvent> {

    private final Queue<SessionEvent> buffer = new ArrayDeque<>();

    private volatile Consumer<SessionEvent> eventConsumer;

    public TemporaryBufferedPageCommands() {
        this.eventConsumer = buffer::add;
    }

    @Override
    public void accept(SessionEvent event) {
       this. eventConsumer.accept(event);
    }

    public synchronized void redirectMessagesOut(final Consumer<SessionEvent> directRemoteOut) {
        eventConsumer = Objects.requireNonNull(directRemoteOut);
        while (!buffer.isEmpty()) {
            final SessionEvent command = buffer.remove();
            eventConsumer.accept(command);
        }
    }
}
