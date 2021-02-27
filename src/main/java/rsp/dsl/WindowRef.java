package rsp.dsl;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.page.EventContext;
import rsp.ref.ElementRef;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A reference to a browser's page window object.
 */
public final class WindowRef implements ElementRef {

    /**
     * Registers an listener on a window object event with the 'prevent-default' property set to true
     * @param eventType a event's name
     * @param handler a code handler for this event
     * @return an event definition
     */
    public EventDefinition on(String eventType, Consumer<EventContext> handler) {
        return on(eventType, EventDefinition.PREVENT_DEFAULT_DEFAULT_VALUE, handler);
    }

    /**
     * Registers an listener on a window object event.
     * @param eventType a event's name
     * @param preventDefault true if the event does not get explicitly handled,
     *                       its default action should not be taken as it normally would be, false otherwise
     * @param handler a code handler for this event
     * @return an event definition
     */
    public EventDefinition on(String eventType, boolean preventDefault, Consumer<EventContext> handler) {
        return new EventDefinition(Optional.of(VirtualDomPath.WINDOW), eventType, handler, preventDefault, Event.NO_MODIFIER);
    }

    public ElementRef ref() {
        return this;
    }
}
