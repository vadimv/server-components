package rsp.dom;

import rsp.page.EventContext;

import java.util.Objects;
import java.util.function.Consumer;

public final class Event<S> {
    public static final Modifier NO_MODIFIER = new NoModifier();
    public final Target eventTarget;
    public final Consumer<EventContext<S>> eventHandler;
    public final boolean preventDefault;
    public final Modifier modifier;
    public final Consumer<S> componentSetState;

    public Event(Event.Target eventTarget,
                 Consumer<EventContext<S>> eventHandler,
                 boolean preventDefault, Modifier modifier,
                 Consumer<S> componentSetState) {
        this.eventTarget = Objects.requireNonNull(eventTarget);
        this.eventHandler = Objects.requireNonNull(eventHandler);
        this.preventDefault = preventDefault;
        this.modifier = Objects.requireNonNull(modifier);
        this.componentSetState = Objects.requireNonNull(componentSetState);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return preventDefault == event.preventDefault &&
                Objects.equals(eventTarget, event.eventTarget) &&
                Objects.equals(modifier, event.modifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventTarget, preventDefault, modifier);
    }

    public static final class Target {
        public Target(String eventType, VirtualDomPath elementPath) {
            this.eventType = Objects.requireNonNull(eventType);
            this.elementPath = Objects.requireNonNull(elementPath);
        }

        public final String eventType;
        public final VirtualDomPath elementPath;

        @Override
        public boolean equals(Object o) {
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

    public static class NoModifier implements Modifier {
    }

    public static final class ThrottleModifier implements Modifier {
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

    public static final class DebounceModifier implements Modifier {
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
