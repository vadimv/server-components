package rsp;

import rsp.dom.Path;

import java.util.Objects;
import java.util.function.Consumer;

public class Event<S> {
    public static final Modifier NO_MODIFIER = new NoModifier();
    public final Target eventTarget;
    public final Consumer<EventContext> eventHandler;
    public final Modifier modifier;

    public Event(Event.Target eventTarget, Consumer<EventContext> eventHandler, Modifier modifier) {
        this.eventTarget = eventTarget;
        this.eventHandler = eventHandler;
        this.modifier = modifier;
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
            return eventType.equals(target.eventType) &&
                    elementPath.equals(target.elementPath);
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
        private final int timeFrameMs;

        public ThrottleModifier(int timeFrameMs) {
            this.timeFrameMs = timeFrameMs;
        }
    }

    public static class DebounceModifier implements Modifier {
        private final int waitMs;
        private final boolean immediate;

        public DebounceModifier(int waitMs, boolean immediate) {
            this.waitMs = waitMs;
            this.immediate = immediate;
        }
    }
}
