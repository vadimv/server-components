package rsp.component;

import rsp.dom.DomEventEntry;
import rsp.page.EventContext;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Interface for subscribing to component events.
 *
 * <p>All handler-registration methods return a {@link Lookup.Registration}.
 * Calling {@link Lookup.Registration#unsubscribe()} removes <em>only</em> that
 * specific registration — never another handler that happens to share the
 * same event name. There is no remove-by-name primitive: a previous
 * {@code removeComponentEventHandler(String)} (remove-first-by-name) was
 * removed because it could silently drop sibling registrations after a
 * parent re-render — see the regression tests in
 * {@code ContextLookupTests.RegistrationTests} and
 * {@code ComponentSegmentTests.EventHandlerRegistrationTests}.</p>
 */
public interface Subscriber {
    void addWindowEventHandler(final String eventType,
                               final Consumer<EventContext> eventHandler,
                               final boolean preventDefault,
                               final DomEventEntry.Modifier modifier);

    /**
     * Register a handler for a component event by name.
     *
     * @param eventType the event name or pattern (supports ".*" wildcard suffix)
     * @param eventHandler the handler to invoke
     * @param preventDefault (currently unused for component events)
     * @return a {@link Lookup.Registration} that removes this specific handler when invoked
     */
    Lookup.Registration addComponentEventHandler(final String eventType,
                                                 final Consumer<ComponentEventEntry.EventContext> eventHandler,
                                                 final boolean preventDefault);

    // ========================================================================
    // Type-safe event handlers using EventKey
    // ========================================================================

    /**
     * Register a type-safe handler for a component event.
     *
     * <p>Example:</p>
     * <pre>{@code
     * subscriber.addEventHandler(EventKeys.FORM_SUBMITTED, (name, values) -> {
     *     // values is already typed as Map<String, Object>
     *     String title = (String) values.get("title");
     * }, false);
     * }</pre>
     *
     * @param <T> the payload type
     * @param key the typed event key
     * @param handler receives the event name and typed payload
     * @param preventDefault (currently unused for component events)
     * @return a {@link Lookup.Registration} that removes this specific handler when invoked
     */
    default <T> Lookup.Registration addEventHandler(final EventKey<T> key,
                                                    final BiConsumer<String, T> handler,
                                                    final boolean preventDefault) {
        return addComponentEventHandler(key.name(), ctx -> {
            @SuppressWarnings("unchecked")
            T payload = (T) ctx.eventObject();
            handler.accept(ctx.eventName(), payload);
        }, preventDefault);
    }

    /**
     * Register a type-safe handler for a void event (no payload).
     *
     * <p>Example:</p>
     * <pre>{@code
     * subscriber.addEventHandler(EventKeys.MODAL_CLOSED, () -> {
     *     closeModal();
     * }, false);
     * }</pre>
     *
     * @param key the void event key
     * @param handler the handler to invoke (receives no arguments)
     * @param preventDefault (currently unused for component events)
     * @return a {@link Lookup.Registration} that removes this specific handler when invoked
     */
    default Lookup.Registration addEventHandler(final EventKey.VoidKey key,
                                                final Runnable handler,
                                                final boolean preventDefault) {
        return addComponentEventHandler(key.name(), ctx -> handler.run(), preventDefault);
    }

    /**
     * Register a type-safe handler for a component event (convenience overload without preventDefault).
     *
     * @param <T> the payload type
     * @param key the typed event key
     * @param handler receives the event name and typed payload
     * @return a {@link Lookup.Registration} that removes this specific handler when invoked
     */
    default <T> Lookup.Registration addEventHandler(final EventKey<T> key,
                                                    final BiConsumer<String, T> handler) {
        return addEventHandler(key, handler, false);
    }

    /**
     * Register a type-safe handler for a void event (convenience overload without preventDefault).
     *
     * @param key the void event key
     * @param handler the handler to invoke
     * @return a {@link Lookup.Registration} that removes this specific handler when invoked
     */
    default Lookup.Registration addEventHandler(final EventKey.VoidKey key,
                                                final Runnable handler) {
        return addEventHandler(key, handler, false);
    }
}
