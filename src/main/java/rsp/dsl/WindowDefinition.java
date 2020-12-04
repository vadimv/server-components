package rsp.dsl;

import rsp.dom.Event;
import rsp.services.EventContext;

import java.util.function.Consumer;

public final class WindowDefinition implements Ref {

    public EventDefinition on(String eventType, Consumer<EventContext> handler) {
        return new EventDefinition(EventDefinition.EventElementMode.WINDOW, eventType, handler, Event.NO_MODIFIER);
    }

    public Ref ref() {
        return this;
    }
}
