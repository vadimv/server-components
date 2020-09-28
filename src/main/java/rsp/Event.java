package rsp;

import rsp.dom.Path;

import java.util.function.Consumer;

public class Event<S> {
    public static final Modifier NO_MODIFIER = new NoModifier();

    public final String eventType;
    public final Path elementPath;
    public final Consumer<EventContext> eventHandler;
    public final Modifier modifier;

    public Event(String eventType, Path elementPath, Consumer<EventContext> eventHandler, Modifier modifier) {
        this.eventType = eventType;
        this.elementPath = elementPath;
        this.eventHandler = eventHandler;
        this.modifier = modifier;
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
