package rsp.compositions.routing;

import rsp.component.*;
import rsp.component.definitions.AddressBarSyncComponent;
import rsp.component.definitions.ContextStateComponent;
import rsp.page.events.RemoteCommand;
import rsp.server.Path;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;

import java.util.*;
import java.util.function.BiFunction;

import static rsp.component.definitions.ContextStateComponent.STATE_UPDATED_EVENT_PREFIX;

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

    /**
     * Tracks the URL set by an UPDATE_PATH_ONLY event so the next query-param
     * update uses the correct base URL instead of the (possibly stale) state.
     * Set and cleared on the event-loop thread only — no synchronization needed.
     */
    private RelativeUrl pendingUrl = null;

    /**
     * Payload for SET_PATH events.
     *
     * @param url the target URL (path + query + fragment — callers must decide all three)
     * @param mode whether to update component state (re-render subtree) or update URL only
     */
    public record PathUpdate(RelativeUrl url, PathUpdateMode mode) {
        public PathUpdate {
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(mode, "mode");
        }
    }

    public enum PathUpdateMode {
        RE_RENDER_SUBTREE,
        UPDATE_PATH_ONLY
    }

    public static final EventKey.SimpleKey<PathUpdate> SET_PATH =
            new EventKey.SimpleKey<>("setPath", PathUpdate.class);

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

            // Store the full Path object for routing components
            enrichedContext = enrichedContext.with(
                new ContextKey.StringKey<>("url.path", Path.class),
                relativeUrl.path()
            );

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

    @Override
    public void onAfterRendered(RelativeUrl state,
                                Subscriber subscriber,
                                CommandsEnqueue commandsEnqueue,
                                StateUpdate<RelativeUrl> stateUpdate) {
        super.onAfterRendered(state, subscriber, commandsEnqueue, stateUpdate);
        subscribeForQueryParameterUpdates(subscriber, commandsEnqueue, stateUpdate);
        subscribeForPathElementUpdates(state, subscriber, commandsEnqueue, stateUpdate);
        subscribeForNavigationEvents(subscriber, commandsEnqueue, stateUpdate);
        subscribeForReloadEvents(subscriber, commandsEnqueue);
    }

    /**
     * Subscribe to navigation events for SPA-style navigation.
     * Event name: "navigate" with payload being the target URL path.
     * <p>
     * <b>Smart routing:</b>
     * <ul>
     *   <li>Internal paths (relative URLs): Uses PushHistory for SPA navigation (no page reload)</li>
     *   <li>External URLs (http://, https://, //): Uses SetHref for full page reload</li>
     * </ul>
     * <p>
     * This enables Single Page Application behavior for internal navigation while preserving
     * traditional full-page navigation for external links.
     */
    private void subscribeForNavigationEvents(Subscriber subscriber,
                                              CommandsEnqueue commandsEnqueue,
                                              StateUpdate<RelativeUrl> stateUpdate) {
        subscriber.addEventHandler(SET_PATH, (eventName, pathUpdate) -> {
            final RelativeUrl target = pathUpdate.url();
            if (pathUpdate.mode() == PathUpdateMode.RE_RENDER_SUBTREE) {
                // Full navigation: update state (triggers subtree re-render) + push history.
                // Clear pendingUrl — state is being updated so it is no longer needed as a base.
                pendingUrl = null;
                stateUpdate.applyStateTransformation(url -> {
                    commandsEnqueue.offer(new RemoteCommand.PushHistory(target.toString()));
                    return target;
                });
            } else {
                // URL-only update: push browser history without re-rendering.
                // Track the new URL so the next query-param update uses it as
                // the base rather than the (now stale) internal state.
                pendingUrl = target;
                commandsEnqueue.offer(new RemoteCommand.PushHistory(target.toString()));
            }
        }, false);
    }

    /**
     * Subscribe to reload events that trigger full page reload.
     * Event name: "reload" with payload being the target URL path.
     * <p>
     * Use this when you explicitly need a full page reload (e.g., after logout, critical errors).
     * For normal navigation, use "navigate" event which provides SPA behavior.
     */
    private void subscribeForReloadEvents(Subscriber subscriber,
                                          CommandsEnqueue commandsEnqueue) {
        subscriber.addComponentEventHandler("reload",
            eventContext -> {
                final Object pathObject = eventContext.eventObject();
                if (pathObject instanceof String path) {
                    commandsEnqueue.offer(new RemoteCommand.SetHref(path));
                }
            },
            false);
    }

    /**
     * Subscribe to ALL query parameter change events using pattern matching.
     * Matches any event with name "stateUpdated.{paramName}".
     *
     * This is truly automatic - no configuration needed, works for any parameter name.
     */
    private void subscribeForQueryParameterUpdates(Subscriber subscriber,
                                                   CommandsEnqueue commandsEnqueue,
                                                   StateUpdate<RelativeUrl> stateUpdate) {
        // Single handler for ALL query parameter updates using wildcard pattern
        subscriber.addComponentEventHandler(STATE_UPDATED_EVENT_PREFIX + "*",
            eventContext -> {
                // Extract parameter name from event name
                String eventName = eventContext.eventName();
                String paramName = eventName.substring(STATE_UPDATED_EVENT_PREFIX.length());

                final Object valueObject = eventContext.eventObject();
                if (valueObject instanceof ContextStateComponent.ContextValue.StringValue stringValue) {
                    // If a SET_PATH UPDATE_PATH_ONLY arrived since the last render,
                    // use that URL as the base so routing is not stale.
                    final RelativeUrl pending = pendingUrl;
                    pendingUrl = null;
                    stateUpdate.applyStateTransformation(currentState -> {
                        RelativeUrl base = pending != null ? pending : currentState;
                        RelativeUrl updatedUrl = updateQueryParameter(base, paramName, stringValue.value());
                        commandsEnqueue.offer(new RemoteCommand.PushHistory(updatedUrl.toString()));
                        return updatedUrl;
                    });
                }
            },
            true);
    }

    /**
     * Subscribe to ALL path element change events.
     * Event names: "stateUpdated.url.path.{index}" (e.g., "stateUpdated.url.path.0")
     */
    private void subscribeForPathElementUpdates(RelativeUrl initialUrl,
                                                Subscriber subscriber,
                                                CommandsEnqueue commandsEnqueue,
                                                StateUpdate<RelativeUrl> stateUpdate) {
        // Subscribe for each path element position
        for (int i = 0; i < initialUrl.path().elementsCount(); i++) {
            final int index = i;
            subscriber.addComponentEventHandler(STATE_UPDATED_EVENT_PREFIX + "url.path." + i,
                eventContext -> {
                    final Object valueObject = eventContext.eventObject();
                    if (valueObject instanceof ContextStateComponent.ContextValue.StringValue stringValue) {
                        stateUpdate.applyStateTransformation(currentState -> {
                            RelativeUrl updatedUrl = updatePathElement(currentState, index, stringValue.value());
                            commandsEnqueue.offer(new RemoteCommand.PushHistory(updatedUrl.toString()));
                            return updatedUrl;
                        });
                    }
                },
                true);
        }
    }

    private static RelativeUrl updateQueryParameter(RelativeUrl oldUrl, String paramName, String newValue) {
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

    private static RelativeUrl updatePathElement(RelativeUrl oldUrl, int index, String newValue) {
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

}
