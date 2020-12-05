package rsp.dsl;

import rsp.dom.Event;
import rsp.dom.Path;
import rsp.page.EventContext;

import java.util.Optional;
import java.util.function.Consumer;

public final class WindowDefinition implements Ref {

    public EventDefinition on(String eventType, Consumer<EventContext> handler) {
        return new EventDefinition(Optional.of(Path.WINDOW), eventType, handler, true, Event.NO_MODIFIER);
    }

    public Ref ref() {
        return this;
    }
}
