package rsp.dom;

import rsp.page.EventContext;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Represents an event interest entry.
 */
public final class EventEntry {
    public static final Modifier NO_MODIFIER = new NoModifier();

    public final String eventName;
    public final Target eventTarget;
    public final Consumer<EventContext> eventHandler;
    public final boolean preventDefault;
    public final Modifier modifier;

    public EventEntry(final String eventName, final EventEntry.Target eventTarget, final Consumer<EventContext> eventHandler, final boolean preventDefault, final Modifier modifier) {
        this.eventName = Objects.requireNonNull(eventName);
        this.eventTarget = Objects.requireNonNull(eventTarget);
        this.eventHandler = Objects.requireNonNull(eventHandler);
        this.preventDefault = preventDefault;
        this.modifier = Objects.requireNonNull(modifier);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final EventEntry event = (EventEntry) o;
        // ignore eventHandler
        return preventDefault == event.preventDefault &&
                Objects.equals(eventTarget, event.eventTarget) &&
                Objects.equals(modifier, event.modifier);
    }

    @Override
    public int hashCode() {
        //ignore eventHandler
        return Objects.hash(eventTarget, preventDefault, modifier);
    }

    public record Target(TreePositionPath elementPath) {
        public Target(final TreePositionPath elementPath) {
            this.elementPath = Objects.requireNonNull(elementPath);
        }

    }

    public sealed interface Modifier {
    }

    public static final class NoModifier implements Modifier {
    }

    public record ThrottleModifier(int timeFrameMs) implements Modifier {
    }

    public record DebounceModifier(int waitMs, boolean immediate) implements Modifier {
    }
}
