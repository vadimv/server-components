package rsp.page;

import rsp.page.events.Command;

import java.util.*;
import java.util.function.Consumer;

/**
 * Initially commands are enqueued but after a redirect those commands are moved to the new destination and all new commands also sent there.
 */
public final class RedirectableEventsConsumer implements Consumer<Command> {

    private final Queue<Command> queue = new LinkedList<>();
    private Consumer<Command> eventConsumer;

    /**
     * Creates a new instance with an internal queue as the destination for commands.
     */
    public RedirectableEventsConsumer() {
        this.eventConsumer = event -> {
            queue.add(event);
        };
    }

    /**
     * Gets a command and sends it either to internal queue or redirects it.
     * @param command the input command
     */
    @Override
    public synchronized void accept(Command command) {
       this.eventConsumer.accept(command);
    }

    /**
     * Redirects commands to the provided destination.
     * @param newCommandsConsumer where to send commands.
     */
    public synchronized void redirect(final Consumer<Command> newCommandsConsumer) {
        eventConsumer = Objects.requireNonNull(newCommandsConsumer);
        while (!queue.isEmpty()) {
            final Command e = queue.remove();
            eventConsumer.accept(e);

        }
    }
}
