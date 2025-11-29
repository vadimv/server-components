package rsp.page;

import rsp.page.events.Command;

import java.util.*;
import java.util.function.Consumer;

public final class RedirectableEventsConsumer implements Consumer<Command> {

    private final Queue<Command> queue = new LinkedList<>();
    private Consumer<Command> eventConsumer;

    public RedirectableEventsConsumer() {
        this.eventConsumer = event -> {
            queue.add(event);
        };
    }

    @Override
    public synchronized void accept(Command event) {
       this.eventConsumer.accept(event);
    }

    public synchronized void redirect(final Consumer<Command> newCommandsEnqueue) {
        eventConsumer = Objects.requireNonNull(newCommandsEnqueue);
        while (!queue.isEmpty()) {
            final Command e = queue.remove();
            eventConsumer.accept(e);

        }
    }
}
