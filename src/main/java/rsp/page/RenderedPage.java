package rsp.page;

import java.util.Objects;

/**
 * A temporary object used as a container to pass results of an initial rendering to a live session.
 * @param pageBuilder contains rendered UI tree with components and DOM
 * @param commandsEnqueue an object containing initial commands, e.g. events subscriptions
 */
public record RenderedPage(PageBuilder pageBuilder, RedirectableEventsConsumer commandsEnqueue) {
    public RenderedPage(final PageBuilder pageBuilder,
                        final RedirectableEventsConsumer commandsEnqueue) {

        this.pageBuilder = Objects.requireNonNull(pageBuilder);
        this.commandsEnqueue = Objects.requireNonNull(commandsEnqueue);
    }
}
