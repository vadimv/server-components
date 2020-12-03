package rsp.dsl;

import rsp.dom.Event;
import rsp.services.EventContext;
import rsp.services.RenderContext;

import java.util.function.Consumer;

public class EventDefinition<S> extends DocumentPartDefinition {
    public enum EventElementMode {
        FROM_CONTEXT,
        WINDOW
    }

    public final EventElementMode elementMode;
    public final String eventType;
    public final Consumer<EventContext> handler;
    public final Event.Modifier modifier;

    public EventDefinition(EventElementMode elementMode,
                           String eventType,
                           Consumer<EventContext> handler,
                           Event.Modifier modifier) {
        super(DocumentPartKind.OTHER);
        this.elementMode = elementMode;
        this.eventType = eventType;
        this.handler = handler;
        this.modifier = modifier;
    }

    public EventDefinition(String eventType,
                           Consumer<EventContext> handler,
                           Event.Modifier modifier) {
        super(DocumentPartKind.OTHER);
        this.elementMode = EventElementMode.FROM_CONTEXT;
        this.eventType = eventType;
        this.handler = handler;
        this.modifier = modifier;
    }

    @Override
    public void accept(RenderContext renderContext) {
        renderContext.addEvent(elementMode, eventType, handler, modifier);
    }

    public EventDefinition<S> throttle(int timeFrameMs) {
        return new EventDefinition<>(elementMode, eventType, handler, new Event.ThrottleModifier(timeFrameMs));
    }

    public EventDefinition<S> debounce(int waitMs, boolean immediate) {
        return new EventDefinition<>(elementMode, eventType, handler, new Event.DebounceModifier(waitMs, immediate));
    }

    public EventDefinition<S> debounce(int waitMs) {
        return new EventDefinition<>(elementMode, eventType, handler, new Event.DebounceModifier(waitMs, false));
    }
}
