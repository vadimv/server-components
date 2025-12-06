package rsp.dsl;

import rsp.component.ComponentRenderContext;
import rsp.dom.DomEventEntry;
import rsp.dom.TreePositionPath;
import rsp.page.EventContext;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A DOM event's subscription definition.
 */
public final class EventDefinition implements Definition {

    /**
     * Is used when prevent default behaviour is not explicitly specified for an event.
     */
    public static final boolean PREVENT_DEFAULT_DEFAULT_VALUE = true;

    /**
     * The componentPath to the element the event generated on.
     */
    public final Optional<TreePositionPath> elementPath;

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
    public final DomEventEntry.Modifier modifier;

    /**
     * Creates a new instance of an event.
     * @param elementPath the componentPath to the element the event generated on
     * @param eventType the type of the event
     * @param handler the event's handler
     * @param preventDefault if true, then Event.preventDefault() JavaScript method is called on the event object
     *                       on the client side before sending the notification to the server,
     *                       if false Event.preventDefault() is not called.
     * @param modifier the events filter modifier
     */
    public EventDefinition(final Optional<TreePositionPath> elementPath,
                           final String eventType,
                           final Consumer<EventContext> handler,
                           final boolean preventDefault,
                           final DomEventEntry.Modifier modifier) {
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
    public EventDefinition(final String eventType,
                           final Consumer<EventContext> handler,
                           final DomEventEntry.Modifier modifier) {
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
    public EventDefinition(final String eventType,
                           final Consumer<EventContext> handler,
                           final boolean preventDefault,
                           final DomEventEntry.Modifier modifier) {
        super();
        this.elementPath = Optional.empty();
        this.eventType = eventType;
        this.handler = handler;
        this.preventDefault = preventDefault;
        this.modifier = modifier;
    }

    @Override
    public boolean render(final ComponentRenderContext renderContext) {
        if (elementPath.isPresent()) {
            renderContext.addEvent(elementPath.get(), eventType, handler, preventDefault, modifier);
        } else {
            renderContext.addEvent(eventType, handler, preventDefault, modifier);
        }
        return true;
    }

    /**
     * Creates a new modified instance with the throttle event filter enabled.
     * Throttle limits the maximum number of times an event handler can be called over time.
     * The event handler is called periodically, at specified intervals, ignoring every other calls in between these intervals.
     * Use this method to filter scroll, resize and mouse-related events.
     * @param timeFrameMs the throttle interval
     * @return a throttle filtered event definition
     */
    public EventDefinition throttle(final int timeFrameMs) {
        return new EventDefinition(elementPath, eventType, handler, preventDefault, new DomEventEntry.ThrottleModifier(timeFrameMs));
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
    public EventDefinition debounce(final int waitMs, final boolean immediate) {
        return new EventDefinition(elementPath, eventType, handler, preventDefault, new DomEventEntry.DebounceModifier(waitMs, immediate));
    }

    /**
     * Creates a new modified instance with the debounce events filter.
     * The first event propagation is not propagated.
     * @param waitMs the debounce interval
     * @return a debounce filtered event definition
     */
    public EventDefinition debounce(final int waitMs) {
        return new EventDefinition(elementPath, eventType, handler, preventDefault, new DomEventEntry.DebounceModifier(waitMs, false));
    }
}
