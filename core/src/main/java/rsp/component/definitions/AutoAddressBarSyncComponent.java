package rsp.component.definitions;

import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentContext;
import rsp.component.ComponentSegment;
import rsp.component.ContextKey;
import rsp.component.TreeBuilderFactory;
import rsp.dom.DomEventEntry;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.RemoteCommand;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;
import rsp.util.json.JsonDataType;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static rsp.component.definitions.ContextStateComponent.STATE_UPDATED_EVENT_PREFIX;
import static rsp.page.PageBuilder.WINDOW_DOM_PATH;

/**
 * An AddressBarSyncComponent that automatically populates ALL URL data into the ComponentContext
 * using a standard namespace convention, and provides bidirectional synchronization with the browser's
 * address bar.
 *
 * <p><b>URL to Context Mapping:</b></p>
 * <p>For URL: {@code /posts/123?p=2&sort=desc#top}, the context will contain:</p>
 * <ul>
 *   <li>{@code "url.path.0"} → {@code "posts"}</li>
 *   <li>{@code "url.path.1"} → {@code "123"}</li>
 *   <li>{@code "url.query.p"} → {@code "2"}</li>
 *   <li>{@code "url.query.sort"} → {@code "desc"}</li>
 *   <li>{@code "url.fragment"} → {@code "top"}</li>
 * </ul>
 *
 * <p><b>Bidirectional Synchronization:</b></p>
 * <p>This component automatically synchronizes state changes back to the URL using
 * <b>wildcard pattern matching</b>. When any component emits a {@code "stateUpdated.*"} event,
 * the URL is updated accordingly:</p>
 *
 * <pre>{@code
 * // Component emits event to change pagination
 * commandsEnqueue.accept(new ComponentEventNotification(
 *     "stateUpdated.p",
 *     new StringValue("3")
 * ));
 * // → URL automatically updates: /posts?p=3
 *
 * // Component emits event to change sorting
 * commandsEnqueue.accept(new ComponentEventNotification(
 *     "stateUpdated.sort",
 *     new StringValue("desc")
 * ));
 * // → URL automatically updates: /posts?p=3&sort=desc
 * }</pre>
 *
 * <p>The wildcard pattern {@code "stateUpdated.*"} matches ANY query parameter event,
 * making the synchronization truly automatic - no configuration needed for new parameters.</p>
 *
 * <p><b>Benefits:</b></p>
 * <ul>
 *   <li><b>Zero configuration:</b> No need to pre-register query parameter names</li>
 *   <li><b>Truly automatic:</b> Works with any query parameter name (standard or custom)</li>
 *   <li><b>Bidirectional sync:</b> URL changes update state, state changes update URL</li>
 *   <li><b>Browser integration:</b> Back/forward buttons work automatically</li>
 *   <li><b>Standard namespace:</b> All URL data discoverable using consistent keys</li>
 *   <li><b>Future-proof:</b> Adding new query parameters requires no code changes</li>
 * </ul>
 *
 * <p><b>Implementation Details:</b></p>
 * <p>Uses event pattern matching (introduced in framework version 3.1.0) to subscribe to
 * all {@code "stateUpdated.*"} events with a single handler, eliminating the need to
 * enumerate specific parameter names.</p>
 *
 * @see rsp.component.ComponentEventEntry
 * @see AddressBarSyncComponent
 */
public abstract class AutoAddressBarSyncComponent extends AddressBarSyncComponent {

    private static final String HISTORY_ENTRY_CHANGE_EVENT_NAME = "popstate";

    public AutoAddressBarSyncComponent(final RelativeUrl initialRelativeUrl) {
        super(initialRelativeUrl);
    }

    /**
     * Returns an empty list - not used in auto-population mode.
     * All path elements are automatically populated with keys like "url.path.0", "url.path.1", etc.
     */
    @Override
    public List<PositionKey> pathElementsPositionKeys() {
        return List.of();
    }

    /**
     * Returns an empty list - not used in auto-population mode.
     * All query parameters are automatically populated with keys like "url.query.{paramName}".
     */
    @Override
    public List<ParameterNameKey> queryParametersNamedKeys() {
        return List.of();
    }

    /**
     * Automatically populates the ComponentContext with ALL URL data using standard namespaces:
     * <ul>
     *   <li>Path elements: {@code "url.path.0"}, {@code "url.path.1"}, ...</li>
     *   <li>Query parameters: {@code "url.query.{name}"}</li>
     *   <li>Fragment: {@code "url.fragment"}</li>
     * </ul>
     */
    @Override
    public BiFunction<ComponentContext, RelativeUrl, ComponentContext> subComponentsContext() {
        return (componentContext, relativeUrl) -> {
            ComponentContext enrichedContext = componentContext;

            // Auto-populate ALL path elements with namespace "url.path.{index}"
            for (int i = 0; i < relativeUrl.path().elementsCount(); i++) {
                final String pathElement = relativeUrl.path().get(i);
                if (pathElement != null) {
                    enrichedContext = enrichedContext.with(
                        new ContextKey.StringKey<>("url.path." + i, String.class),
                        pathElement
                    );
                }
            }

            // Auto-populate ALL query parameters with namespace "url.query.{name}"
            for (final Query.Parameter param : relativeUrl.query().parameters()) {
                enrichedContext = enrichedContext.with(
                    new ContextKey.StringKey<>("url.query." + param.name(), String.class),
                    param.value()
                );
            }

            // Fragment (if present) with namespace "url.fragment"
            if (relativeUrl.fragment() != null &&
                !relativeUrl.fragment().isEmpty()) {
                enrichedContext = enrichedContext.with(
                    new ContextKey.StringKey<>("url.fragment", String.class),
                    relativeUrl.fragment().fragmentString()
                );
            }

            return enrichedContext;
        };
    }

    /**
     * Override to add automatic event subscriptions for ALL query parameters and path elements.
     * This enables bidirectional sync: URL changes update state, and state changes update URL.
     */
    @Override
    public ComponentSegment<RelativeUrl> createComponentSegment(final QualifiedSessionId sessionId,
                                                                final TreePositionPath componentPath,
                                                                final TreeBuilderFactory treeBuilderFactory,
                                                                final ComponentContext componentContext,
                                                                final Consumer<Command> commandsEnqueue) {
        super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
        final ComponentCompositeKey componentId = new ComponentCompositeKey(sessionId, componentType, componentPath);

        return new ComponentSegment<>(componentId,
                                      initStateSupplier(),
                                      subComponentsContext(),
                                      componentView(),
                                      this,
                                      treeBuilderFactory,
                                      componentContext,
                                      commandsEnqueue) {

            @Override
            protected void onAfterRendered(final RelativeUrl state) {
                subscribeForBrowserHistoryEvents();
                subscribeForQueryParameterUpdates(state);
                subscribeForPathElementUpdates(state);
            }

            /**
             * Subscribe to ALL query parameter change events using pattern matching.
             * Matches any event with name "stateUpdated.{paramName}".
             *
             * This is truly automatic - no configuration needed, works for any parameter name.
             */
            private void subscribeForQueryParameterUpdates(RelativeUrl currentUrl) {
                // Single handler for ALL query parameter updates using wildcard pattern
                this.addComponentEventHandler(STATE_UPDATED_EVENT_PREFIX + "*",
                    eventContext -> {
                        // Extract parameter name from event name
                        String eventName = eventContext.eventName();
                        String paramName = eventName.substring(STATE_UPDATED_EVENT_PREFIX.length());

                        final Object valueObject = eventContext.eventObject();
                        if (valueObject instanceof ContextStateComponent.ContextValue.StringValue stringValue) {
                            // Update the query parameter
                            RelativeUrl updatedUrl = updateQueryParameter(getState(), paramName, stringValue.value());

                            // Push to browser history
                            this.commandsEnqueue.accept(new RemoteCommand.PushHistory(updatedUrl.toString()));

                            // Update component state to trigger re-render
                            setState(updatedUrl);
                        }
                    },
                    true);
            }

            /**
             * Subscribe to ALL path element change events.
             * Event names: "stateUpdated.url.path.{index}" (e.g., "stateUpdated.url.path.0")
             */
            private void subscribeForPathElementUpdates(RelativeUrl currentUrl) {
                // Subscribe for each path element position
                for (int i = 0; i < currentUrl.path().elementsCount(); i++) {
                    final int index = i;
                    this.addComponentEventHandler(STATE_UPDATED_EVENT_PREFIX + "url.path." + i,
                        eventContext -> {
                            final Object valueObject = eventContext.eventObject();
                            if (valueObject instanceof ContextStateComponent.ContextValue.StringValue stringValue) {
                                // Update the path element
                                RelativeUrl updatedUrl = updatePathElement(getState(), index, stringValue.value());

                                // Push to browser history
                                this.commandsEnqueue.accept(new RemoteCommand.PushHistory(updatedUrl.toString()));

                                // Update component state to trigger re-render
                                setState(updatedUrl);
                            }
                        },
                        true);
                }
            }

            /**
             * Update a query parameter in the URL.
             */
            private RelativeUrl updateQueryParameter(RelativeUrl oldUrl, String paramName, String newValue) {
                Map<String, String> params = new HashMap<>();

                // Copy existing parameters
                for (Query.Parameter param : oldUrl.query().parameters()) {
                    params.put(param.name(), param.value());
                }

                // Update the changed parameter
                params.put(paramName, newValue);

                // Build new query
                Query newQuery = new Query(params.entrySet().stream()
                    .map(e -> new Query.Parameter(e.getKey(), e.getValue()))
                    .toList());

                return new RelativeUrl(oldUrl.path(), newQuery, oldUrl.fragment());
            }

            /**
             * Update a path element in the URL.
             */
            private RelativeUrl updatePathElement(RelativeUrl oldUrl, int index, String newValue) {
                List<String> elements = new ArrayList<>();

                // Copy existing path elements
                for (int i = 0; i < oldUrl.path().elementsCount(); i++) {
                    elements.add(oldUrl.path().get(i));
                }

                // Update the changed element
                if (index < elements.size()) {
                    elements.set(index, newValue);
                }

                Path newPath = new Path(oldUrl.path().isAbsolute(), elements.toArray(new String[0]));

                return new RelativeUrl(newPath, oldUrl.query(), oldUrl.fragment());
            }

            /**
             * Subscribe to browser history events (back/forward buttons).
             */
            private void subscribeForBrowserHistoryEvents() {
                this.addDomEventHandler(WINDOW_DOM_PATH,
                                        HISTORY_ENTRY_CHANGE_EVENT_NAME,
                             eventContext -> {
                                 final RelativeUrl newRelativeUrl = extractRelativeUrl(eventContext.eventObject());
                                 setState(newRelativeUrl);
                             },
                             true,
                              DomEventEntry.NO_MODIFIER);
            }

            /**
             * Extract RelativeUrl from browser history event.
             */
            private static RelativeUrl extractRelativeUrl(final JsonDataType.Object eventObject) {
                if (eventObject.value("path") instanceof JsonDataType.String(String path)
                    && eventObject.value("query") instanceof JsonDataType.String(String query)
                    && eventObject.value("fragment") instanceof JsonDataType.String(String fragment)) {
                    return new RelativeUrl(Path.of(path), Query.of(query), new Fragment(fragment));
                } else {
                    throw new JsonDataType.JsonException("Error unpacking JSON event object:" + eventObject);
                }
            }
        };
    }
}
