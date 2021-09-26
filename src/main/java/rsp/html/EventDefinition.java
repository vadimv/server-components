package rsp.html;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.page.EventContext;
import rsp.page.PageRenderContext;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A DOM event subscription definition.
 */
public final class EventDefinition implements DocumentPartDefinition {

    /**
     * Is used when prevent default behaviour is not explicitly specified for an event.
     */
    public static boolean PREVENT_DEFAULT_DEFAULT_VALUE = true;

    /**
     * The path to the element the event generated on.
     */
    public final Optional<VirtualDomPath> elementPath;

    /**
     * The event's type.
     */
    public final String eventType;

    /**
     * The event's handler.
     */
    public final Consumer<EventContext> handler;

    /**
     * if true, then Event.preventDefault() JavaScript method is called on the event object
     * on the client side before sending the notification to the server,
     * if false Event.preventDefault() is not called.
     */
    public final boolean preventDefault;

    /**
     * Defines how multiple events to be handled in a given period of time.
     */
    public final Event.Modifier modifier;

    /**
     * Creates a new instance of an event.
     * @param elementPath the path to the element the event generated on
     * @param eventType the type of the event
     * @param handler the event's handler
     * @param preventDefault if true, then Event.preventDefault() JavaScript method is called on the event object
     *                       on the client side before sending the notification to the server,
     *                       if false Event.preventDefault() is not called.
     * @param modifier the events filter modifier
     */
    public EventDefinition(Optional<VirtualDomPath> elementPath,
                           String eventType,
                           Consumer<EventContext> handler,
                           boolean preventDefault,
                           Event.Modifier modifier) {
        super();
        this.elementPath = elementPath;
        this.eventType = eventType;
        this.handler = handler;
        this.preventDefault = preventDefault;
        this.modifier = modifier;
    }

    /**
     * Creates a new instance of an event subscription.
     *
     * @param eventType the type of the event
     * @param handler the event's handler
     * @param modifier the event's filter
     */
    public EventDefinition(String eventType,
                           Consumer<EventContext> handler,
                           Event.Modifier modifier) {
        super();
        this.elementPath = Optional.empty();
        this.eventType = eventType;
        this.handler = handler;
        this.preventDefault = PREVENT_DEFAULT_DEFAULT_VALUE;
        this.modifier = modifier;
    }

    /**
     * Creates a new instance of an event subscription.
     * @param eventType the type of the event
     * @param handler the event's handler
     * @param preventDefault if true, then Event.preventDefault() JavaScript method is called on the event object
     *                       on the client side before sending the notification to the server,
     *                       if false Event.preventDefault() is not called.
     * @param modifier the event's filter
     */
    public EventDefinition(String eventType,
                           Consumer<EventContext> handler,
                           boolean preventDefault,
                           Event.Modifier modifier) {
        super();
        this.elementPath = Optional.empty();
        this.eventType = eventType;
        this.handler = handler;
        this.preventDefault = preventDefault;
        this.modifier = modifier;
    }

    @Override
    public void accept(PageRenderContext renderContext) {
        renderContext.addEvent(elementPath, eventType, handler, preventDefault, modifier);
    }

    /**
     * Creates a new modified instance with the throttle event filter enabled.
     * Throttle limits the maximum number of times an event handler can be called over time.
     * The event handler is called periodically, at specified intervals, ignoring every other calls in between these intervals.
     * Use this method to filter scroll, resize and mouse-related events.
     * @param timeFrameMs the throttle interval
     * @return a throttle filtered event definition
     */
    public EventDefinition throttle(int timeFrameMs) {
        return new EventDefinition(elementPath, eventType, handler, preventDefault, new Event.ThrottleModifier(timeFrameMs));
    }

    /**
     * Creates a new modified instance with the debounce events filter.
     * The debounce filter controls events being triggered successively and, if the interval between two sequential occurrences is less than the provided interval.
     * The first event propagation can be enabled or not by the method's parameter.
     * This process is repeated until it finds a pause greater than or equal to the provided interval.
     * Only the last event before the pause will be fired, ignoring all the previous ones.
     * Use this method, for example, for an autocomplete input text field.
     * @param waitMs the debounce interval
     * @param immediate true if the first event should be fired and false otherwise
     * @return a debounce filtered event definition
     */
    public EventDefinition debounce(int waitMs, boolean immediate) {
        return new EventDefinition(elementPath, eventType, handler, preventDefault, new Event.DebounceModifier(waitMs, immediate));
    }

    /**
     * Creates a new modified instance with the debounce events filter.
     * The first event propagation is not propagated.
     * @param waitMs the debounce interval
     * @return a debounce filtered event definition
     */
    public EventDefinition debounce(int waitMs) {
        return new EventDefinition(elementPath, eventType, handler, preventDefault, new Event.DebounceModifier(waitMs, false));
    }
}
