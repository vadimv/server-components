package rsp.dsl;

import rsp.component.TreeBuilder;
import rsp.dom.DomEventEntry;
import rsp.page.EventContext;
import rsp.ref.ElementRef;

import java.util.Objects;
import java.util.function.Consumer;

import static rsp.page.PageBuilder.WINDOW_DOM_PATH;

/**
 * Represents a browser's page window object.
 */
public final class Window {

    private final ElementRef WINDOW_REFERENCE = new WindowRef();

    /**
     * Registers a listener on a window object event with the 'prevent-default' property set to true
     * @param eventType a event's name
     * @param handler a code handler for this event
     * @return an event definition
     */
    public Definition on(final String eventType, final Consumer<EventContext> handler) {
        return on(eventType, true, handler);
    }

    /**
     * Registers an listener on a window object event.
     * @param eventType a event's name
     * @param preventDefault true if the event does not get explicitly handled,
     *                       its default action should not be taken as it normally would be, false otherwise
     * @param handler a code handler for this event
     * @return an event definition
     */
    public Definition on(final String eventType, final boolean preventDefault, final Consumer<EventContext> handler) {
        return new WindowEventDefinition(eventType, handler, preventDefault, DomEventEntry.NO_MODIFIER);
    }

    public ElementRef ref() {
        return WINDOW_REFERENCE;
    }

    public static final class WindowRef implements ElementRef {}

    /**
     * A special-purpose event definition for events attached to the global Window object.
     */
    private record WindowEventDefinition(String eventType,
                                         Consumer<EventContext> handler,
                                         boolean preventDefault,
                                         DomEventEntry.Modifier modifier) implements Definition {
        private WindowEventDefinition {
            Objects.requireNonNull(eventType);
            Objects.requireNonNull(handler);
            Objects.requireNonNull(modifier);
        }

        @Override
        public void render(final TreeBuilder renderContext) {
            // Always use the hardcoded WINDOW_DOM_PATH for window events
            renderContext.addEvent(WINDOW_DOM_PATH, eventType, handler, preventDefault, modifier);
        }
    }
}
