package rsp.dsl;

import rsp.Event;
import rsp.EventContext;
import rsp.Ref;

import java.util.function.Consumer;

public class WindowDefinition implements Ref {
    public EventDefinition on(String eventType, Consumer<EventContext> handler) {
        return new EventDefinition(EventDefinition.EventElementMode.WINDOW, eventType, handler, Event.NO_MODIFIER);
    }

    public Ref ref() {
        return this;
    }
}
