package rsp.dsl;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.page.EventContext;

import java.util.Optional;
import java.util.function.Consumer;

public final class WindowDefinition implements Ref {

    public EventDefinition on(String eventType, Consumer<EventContext> handler) {
        return new EventDefinition(Optional.of(VirtualDomPath.WINDOW), eventType, handler, true, Event.NO_MODIFIER);
    }

    public Ref ref() {
        return this;
    }
}
