package rsp.html;

import rsp.dom.Event;
import rsp.page.EventContext;
import rsp.page.PageRendering;
import rsp.ref.ElementRef;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Represents a browser's page window object.
 */
public final class WindowDefinition {

    private final ElementRef WINDOW_REFERENCE = new WindowRef();
    /**
     * Registers a listener on a window object event with the 'prevent-default' property set to true
     * @param eventType a event's name
     * @param handler a code handler for this event
     * @return an event definition
     */
    public EventDefinition on(final String eventType, final Consumer<EventContext> handler) {
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
    public EventDefinition on(final String eventType, final boolean preventDefault, final Consumer<EventContext> handler) {
        return new EventDefinition(Optional.of(PageRendering.WINDOW_DOM_PATH), eventType, handler, preventDefault, Event.NO_MODIFIER);
    }

    public ElementRef ref() {
        return WINDOW_REFERENCE;
    }

    public static final class WindowRef implements ElementRef {}
}
