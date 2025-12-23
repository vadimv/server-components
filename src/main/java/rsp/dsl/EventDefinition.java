package rsp.dsl;

import rsp.component.TreeBuilder;
import rsp.dom.DomEventEntry;
import rsp.page.EventContext;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * An event definition.
 * @param eventType the event's type, e.g. "click"
 * @param handler the event's handler
 * @param preventDefault true if the event does not get explicitly handled,
 *                       its default action should not be taken as it normally would be, false otherwise
 * @param modifier an event modifier
 */
public record EventDefinition(String eventType,
                              Consumer<EventContext> handler,
                              boolean preventDefault,
                              DomEventEntry.Modifier modifier) implements Definition {

    public EventDefinition(final String eventType,
                           final Consumer<EventContext> handler,
                           final DomEventEntry.Modifier modifier) {
        this(eventType, handler, false, modifier);
    }

    public EventDefinition {
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(handler);
        Objects.requireNonNull(modifier);
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        renderContext.addEvent(eventType, handler, preventDefault, modifier);
    }
}
