package rsp.dom;

import rsp.page.EventContext;

import java.util.Objects;
import java.util.function.Consumer;

public final class Event {
    public static final Modifier NO_MODIFIER = new NoModifier();
    public final Target eventTarget;
    public final Consumer<EventContext> eventHandler;
    public final boolean preventDefault;
    public final Modifier modifier;

    public Event(Event.Target eventTarget, Consumer<EventContext> eventHandler, boolean preventDefault, Modifier modifier) {
        this.eventTarget = eventTarget;
        this.eventHandler = eventHandler;
        this.preventDefault = preventDefault;
        this.modifier = modifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return eventTarget.equals(event.eventTarget) &&
                modifier.equals(event.modifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventTarget, modifier);
    }

    public static class Target {
        public Target(String eventType, Path elementPath) {
            this.eventType = eventType;
            this.elementPath = elementPath;
        }

        public final String eventType;
        public final Path elementPath;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Target target = (Target) o;
            return eventType.equals(target.eventType)
                    && elementPath.equals(target.elementPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventType, elementPath);
        }
    }

    public interface Modifier {
    }

    public static class NoModifier implements Modifier {
    }

    public static class ThrottleModifier implements Modifier {
        public final int timeFrameMs;

        public ThrottleModifier(int timeFrameMs) {
            this.timeFrameMs = timeFrameMs;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ThrottleModifier that = (ThrottleModifier) o;
            return timeFrameMs == that.timeFrameMs;
        }

        @Override
        public int hashCode() {
            return Objects.hash(timeFrameMs);
        }
    }

    public static class DebounceModifier implements Modifier {
        public final int waitMs;
        public final boolean immediate;

        public DebounceModifier(int waitMs, boolean immediate) {
            this.waitMs = waitMs;
            this.immediate = immediate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DebounceModifier that = (DebounceModifier) o;
            return waitMs == that.waitMs &&
                    immediate == that.immediate;
        }

        @Override
        public int hashCode() {
            return Objects.hash(waitMs, immediate);
        }
    }
}
