package rsp.dsl;

import rsp.EventContext;
import rsp.RenderContext;

import java.util.function.Consumer;

public class EventDefinition<S> extends DocumentPartDefinition {
    public enum EventElementMode {
        FROM_CONTEXT,
        WINDOW
    }

    public final EventElementMode elementMode;
    public final String eventType;
    public final Consumer<EventContext> handler;

    public EventDefinition(EventElementMode elementMode, String eventType, Consumer<EventContext> handler) {
        super(DocumentPartKind.OTHER);
        this.elementMode = elementMode;
        this.eventType = eventType;
        this.handler = handler;
    }

    public EventDefinition(String eventType, Consumer<EventContext> handler) {
        super(DocumentPartKind.OTHER);
        this.elementMode = EventElementMode.FROM_CONTEXT;
        this.eventType = eventType;
        this.handler = handler;
    }

    @Override
    public void accept(RenderContext renderContext) {
        renderContext.addEvent(elementMode, eventType, handler);
    }

}
