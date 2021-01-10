package rsp.dsl;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.page.EventContext;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A reference to a browser's page window object.
 */
public final class WindowRef implements Ref {

    /**
     * Registers an listener on a window object event.
     * @param eventType a event's name
     * @param handler a code handler for this event
     * @return an event definition
     */
    public EventDefinition on(String eventType, Consumer<EventContext> handler) {
        return new EventDefinition(Optional.of(VirtualDomPath.WINDOW), eventType, handler, true, Event.NO_MODIFIER);
    }

    public Ref ref() {
        return this;
    }
}
