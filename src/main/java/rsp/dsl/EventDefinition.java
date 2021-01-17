package rsp.dsl;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.page.EventContext;
import rsp.page.RenderContext;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * An event definition.
 */
public final class EventDefinition extends DocumentPartDefinition {

    /**
     * This value is used when not explicitly specified for an event.
     */
    public static boolean PREVENT_DEFAULT_DEFAULT_VALUE = true;

    public final Optional<VirtualDomPath> elementPath;
    public final String eventType;
    public final Consumer<EventContext> handler;
    public final boolean preventDefault;
    public final Event.Modifier modifier;

    public EventDefinition(Optional<VirtualDomPath> elementPath,
                           String eventType,
                           Consumer<EventContext> handler,
                           boolean preventDefault,
                           Event.Modifier modifier) {
        super(DocumentPartKind.OTHER);
        this.elementPath = elementPath;
        this.eventType = eventType;
        this.handler = handler;
        this.preventDefault = preventDefault;
        this.modifier = modifier;
    }

    public EventDefinition(String eventType,
                           Consumer<EventContext> handler,
                           Event.Modifier modifier) {
        super(DocumentPartKind.OTHER);
        this.elementPath = Optional.empty();
        this.eventType = eventType;
        this.handler = handler;
        this.preventDefault = PREVENT_DEFAULT_DEFAULT_VALUE;
        this.modifier = modifier;
    }

    public EventDefinition(String eventType,
                           Consumer<EventContext> handler,
                           boolean preventDefault,
                           Event.Modifier modifier) {
        super(DocumentPartKind.OTHER);
        this.elementPath = Optional.empty();
        this.eventType = eventType;
        this.handler = handler;
        this.preventDefault = preventDefault;
        this.modifier = modifier;
    }

    @Override
    public void accept(RenderContext renderContext) {
        renderContext.addEvent(elementPath, eventType, handler, preventDefault, modifier);
    }

    public EventDefinition throttle(int timeFrameMs) {
        return new EventDefinition(elementPath, eventType, handler, preventDefault, new Event.ThrottleModifier(timeFrameMs));
    }

    public EventDefinition debounce(int waitMs, boolean immediate) {
        return new EventDefinition(elementPath, eventType, handler, preventDefault, new Event.DebounceModifier(waitMs, immediate));
    }

    public EventDefinition debounce(int waitMs) {
        return new EventDefinition(elementPath, eventType, handler, preventDefault, new Event.DebounceModifier(waitMs, false));
    }
}
