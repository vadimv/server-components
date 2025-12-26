package rsp.component;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A subscription to a component event.
 * @param eventName an event to listen to
 * @param eventHandler a handler to execute to react to the event
 * @param preventDefault (currently unused for component events)
 */
public record ComponentEventEntry(String eventName, Consumer<EventContext> eventHandler, boolean preventDefault) {

    public ComponentEventEntry {
        Objects.requireNonNull(eventName);
        Objects.requireNonNull(eventHandler);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ComponentEventEntry event = (ComponentEventEntry) o;
        // ignore eventHandler
        return Objects.equals(eventName, event.eventName) &&
                preventDefault == event.preventDefault;
    }

    @Override
    public int hashCode() {
        //ignore eventHandler
        return Objects.hash(eventName, preventDefault);
    }

    /**
     * The context for a component event.
     * @param eventObject the data payload of the event
     */
    public record EventContext(Object eventObject) {
        public EventContext {
            Objects.requireNonNull(eventObject);
        }
    }
}
