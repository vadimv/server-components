package rsp.component;

import rsp.dom.DomEventEntry;
import rsp.page.EventContext;

import java.util.function.Consumer;

public interface Subscriber {
    void addWindowEventHandler(final String eventType,
                               final Consumer<EventContext> eventHandler,
                               final boolean preventDefault,
                               final DomEventEntry.Modifier modifier);

    void addComponentEventHandler(final String eventType,
                                  final Consumer<ComponentEventEntry.EventContext> eventHandler,
                                  final boolean preventDefault);
}