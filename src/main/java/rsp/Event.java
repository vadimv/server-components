package rsp;

import rsp.dom.Path;

import java.util.function.Consumer;

public class Event<S> {
    public final String eventType;
    public final Path elementPath;
    public final Consumer<EventContext> eventHandler;

    public Event(String eventType, Path elementPath, Consumer<EventContext> eventHandler) {
        this.eventType = eventType;
        this.elementPath = elementPath;
        this.eventHandler = eventHandler;
    }
}
