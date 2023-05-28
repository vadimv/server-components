package rsp.dom;

import rsp.page.EventContext;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Represents a page event.
 */
public final class Event {
    public static final Modifier NO_MODIFIER = new NoModifier();

    public final Target eventTarget;
    public final Consumer<EventContext> eventHandler;
    public final boolean preventDefault;
    public final Modifier modifier;

    public Event(final Event.Target eventTarget, final Consumer<EventContext> eventHandler, final boolean preventDefault, final Modifier modifier) {
        this.eventTarget = Objects.requireNonNull(eventTarget);
        this.eventHandler = Objects.requireNonNull(eventHandler);
        this.preventDefault = preventDefault;
        this.modifier = Objects.requireNonNull(modifier);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Event event = (Event) o;
        return preventDefault == event.preventDefault &&
                Objects.equals(eventTarget, event.eventTarget) &&
                Objects.equals(modifier, event.modifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventTarget, preventDefault, modifier);
    }

    public static final class Target {
        public Target(final String eventType, final VirtualDomPath elementPath) {
            this.eventType = Objects.requireNonNull(eventType);
            this.elementPath = Objects.requireNonNull(elementPath);
        }

        public final String eventType;
        public final VirtualDomPath elementPath;

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Target target = (Target) o;
            return Objects.equals(eventType, target.eventType) &&
                   Objects.equals(elementPath, target.elementPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventType, elementPath);
        }
    }

    public interface Modifier {
    }

    public static final class NoModifier implements Modifier {
    }

    public static final class ThrottleModifier implements Modifier {
        public final int timeFrameMs;

        public ThrottleModifier(final int timeFrameMs) {
            this.timeFrameMs = timeFrameMs;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final ThrottleModifier that = (ThrottleModifier) o;
            return timeFrameMs == that.timeFrameMs;
        }

        @Override
        public int hashCode() {
            return Objects.hash(timeFrameMs);
        }
    }

    public static final class DebounceModifier implements Modifier {
        public final int waitMs;
        public final boolean immediate;

        public DebounceModifier(final int waitMs, final boolean immediate) {
            this.waitMs = waitMs;
            this.immediate = immediate;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final DebounceModifier that = (DebounceModifier) o;
            return waitMs == that.waitMs && immediate == that.immediate;
        }

        @Override
        public int hashCode() {
            return Objects.hash(waitMs, immediate);
        }
    }
}
