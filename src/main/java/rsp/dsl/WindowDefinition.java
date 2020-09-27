package rsp.dsl;

import rsp.EventContext;

import java.util.function.Consumer;

public class WindowDefinition {
    public EventDefinition event(String eventType, Consumer<EventContext> handler) {
        return new EventDefinition(EventDefinition.EventElementMode.WINDOW, eventType, handler);
    }
}
