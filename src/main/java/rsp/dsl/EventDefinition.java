package rsp.dsl;

import rsp.EventContext;
import rsp.RenderContext;

import java.util.function.Consumer;

public class EventDefinition<S> extends DocumentPartDefinition {
    public final String eventType;
    public final Consumer<EventContext> handler;

    public EventDefinition(String eventType, Consumer<EventContext> handler) {
        super(DocumentPartDefinition.DocumentPartKind.OTHER);
        this.eventType = eventType;
        this.handler = handler;
    }

    @Override
    public void accept(RenderContext renderContext) {
        renderContext.addEvent(eventType, handler);
    }

}
