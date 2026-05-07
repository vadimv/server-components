package rsp.compositions.contract;

import rsp.component.*;
import rsp.compositions.composition.Composition;
import rsp.compositions.layout.PlacementDecision;
import rsp.compositions.routing.AutoAddressBarSyncComponent;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static rsp.compositions.contract.ActionBindings.ShowPayload;
import static rsp.compositions.contract.EventKeys.*;
import static rsp.compositions.routing.AutoAddressBarSyncComponent.PathUpdateMode.PUSH_URL_ONLY;

/**
 * Registers and handles Scene lifecycle events for the base layer (routed + companions).
 * <p>
 * Events handled:
 * <ul>
 *   <li>SHOW - Resolve placement for on-demand contracts</li>
 *   <li>SET_PRIMARY - Replace the routed contract</li>
 *   <li>ACTION_SUCCESS - Refresh routed contract in place (data may have changed)</li>
 * </ul>
 * <p>
 * Layer-specific events (SHOW_LAYER, HIDE, overlay ACTION_SUCCESS) are handled
 * by LayerComponent.
 */
public final class SceneEventHandler {

    private final ComponentContext savedContext;

    public SceneEventHandler(ComponentContext savedContext) {
        this.savedContext = Objects.requireNonNull(savedContext, "savedContext");
    }

    /**
     * Register event handlers for base layer lifecycle management.
     */
    public void registerHandlers(Scene state,
                                 Subscriber subscriber,
                                 CommandsEnqueue commandsEnqueue,
                                 StateUpdate<Scene> stateUpdate) {
        subscriber.addEventHandler(SHOW, (eventName, payload) -> {
            handleShow(state, payload, stateUpdate, commandsEnqueue);
        }, false);

        subscriber.addEventHandler(SET_PRIMARY, (eventName, contractClass) -> {
            handleSetPrimary(state, contractClass, stateUpdate, commandsEnqueue);
        }, false);

        // ACTION_SUCCESS handler: refresh routed contract in place
        subscriber.addEventHandler(ACTION_SUCCESS, (eventName, result) -> {
            handleActionSuccess(state, result, commandsEnqueue, stateUpdate);
        }, false);

        subscriber.addEventHandler(SCENE_QUERY_UPDATED, (eventName, update) -> {
            handleSceneQueryUpdated(state, update, commandsEnqueue, stateUpdate);
        }, false);
    }

    private void handleShow(Scene state,
                            ShowPayload payload,
                            StateUpdate<Scene> stateUpdate,
                            CommandsEnqueue commandsEnqueue) {
        Class<? extends ViewContract> contractClass = payload.contractClass();
        PlacementDecision decision = state.composition().layout().resolvePlacement(contractClass, state);

        if (decision.placement().isInline()) {
            handleShowInline(state, payload, stateUpdate, commandsEnqueue);
            return;
        }

        Lookup lookup = LookupFactory.create(savedContext, commandsEnqueue);
        lookup.publish(SHOW_LAYER, payload);
    }

    private void handleShowInline(Scene state,
                                  ShowPayload payload,
                                  StateUpdate<Scene> stateUpdate,
                                  CommandsEnqueue commandsEnqueue) {
        Class<? extends ViewContract> contractClass = payload.contractClass();

        if (state.routedRuntime() != null
                && state.routedRuntime().contractClass().equals(contractClass)) {
            return;
        }

        // Capture the current routed contract as a return target before destroying it,
        // so a later ACTION_SUCCESS from the inline form can restore the previous view
        // (e.g., Save/Cancel on an inline edit form returns to the list).
        final Scene.InlineReturnTarget returnTarget = captureInlineReturnTarget(state);

        ContractRuntime newRuntime = resolveAndInstantiate(state, contractClass, commandsEnqueue,
                true, payload.data());
        if (newRuntime == null) {
            return;
        }

        if (state.routedRuntime() != null) {
            state.routedRuntime().destroy();
        }

        // Update the URL bar to reflect the now-routed inline contract (e.g. /comments/3).
        // The Router's pattern is the source of truth for URL shape; we substitute path
        // parameters from the SHOW payload data and preserve the current query state.
        RelativeUrl targetUrl = publishInlineUrlUpdate(state, contractClass, payload.data(), commandsEnqueue);

        stateUpdate.applyStateTransformation(s -> {
            Scene next = s.withRoutedRuntime(newRuntime);
            if (targetUrl != null) {
                next = next.withEffectiveUrl(targetUrl);
            }
            return returnTarget != null ? next.withInlineReturnTarget(returnTarget) : next;
        });
    }

    private RelativeUrl publishInlineUrlUpdate(Scene state,
                                               Class<? extends ViewContract> contractClass,
                                               Map<String, Object> showData,
                                               CommandsEnqueue commandsEnqueue) {
        Composition composition = state.composition();
        if (composition == null || composition.router() == null) {
            return null;
        }
        String pattern = composition.router().findRoutePattern(contractClass).orElse(null);
        if (pattern == null) {
            return null;
        }
        String resolvedPath = substitutePathParams(pattern, showData);
        Lookup lookup = LookupFactory.create(savedContext, commandsEnqueue);
        RelativeUrl url = new RelativeUrl(Path.of(resolvedPath), captureQuery(state), captureFragment(state));
        lookup.publish(AutoAddressBarSyncComponent.SET_PATH,
                       new AutoAddressBarSyncComponent.PathUpdate(url, PUSH_URL_ONLY));
        return url;
    }

    /**
     * Substitute {@code :name} placeholders in a route pattern with values from
     * SHOW data. Unknown parameters are left as-is.
     */
    private static String substitutePathParams(String pattern, Map<String, Object> data) {
        if (data == null || data.isEmpty() || !pattern.contains(":")) {
            return pattern;
        }
        String result = pattern;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() == null) continue;
            result = result.replace(":" + entry.getKey(), String.valueOf(entry.getValue()));
        }
        return result;
    }

    /**
     * Capture the URL state of the currently routed contract for later restoration
     * after an inline placement.
     * <p>
     * Returns {@code null} when there is no routed contract or the routed contract
     * has no registered route (e.g., IDE-style UIs without a router).
     */
    private Scene.InlineReturnTarget captureInlineReturnTarget(Scene state) {
        if (state.routedRuntime() == null) {
            return null;
        }
        Composition composition = state.composition();
        if (composition == null || composition.router() == null) {
            return null;
        }
        Class<? extends ViewContract> prevClass = state.routedRuntime().contractClass();
        String prevRoute = composition.router().findRoutePattern(prevClass).orElse(null);
        if (prevRoute == null) {
            return null;
        }
        return new Scene.InlineReturnTarget(prevClass, prevRoute, captureQuery(state), captureFragment(state));
    }

    private Query captureQuery(Scene state) {
        if (state.effectiveUrl() != null) {
            return state.effectiveUrl().query();
        }
        String prefix = ContextKeys.URL_QUERY.baseKey() + ".";
        Map<String, Object> entries = savedContext.stringEntriesWithPrefix(prefix);
        if (entries.isEmpty()) {
            return Query.EMPTY;
        }
        List<Query.Parameter> params = new ArrayList<>(entries.size());
        for (Map.Entry<String, Object> e : entries.entrySet()) {
            String name = e.getKey().substring(prefix.length());
            if (e.getValue() instanceof String value) {
                params.add(new Query.Parameter(name, value));
            }
        }
        return params.isEmpty() ? Query.EMPTY : new Query(params);
    }

    private Fragment captureFragment(Scene state) {
        if (state.effectiveUrl() != null) {
            return state.effectiveUrl().fragment();
        }
        String value = savedContext.get(ContextKeys.URL_FRAGMENT);
        if (value == null || value.isEmpty()) {
            return Fragment.EMPTY;
        }
        return new Fragment(value);
    }

    private void handleSceneQueryUpdated(Scene state,
                                         EventKeys.SceneQueryUpdate update,
                                         CommandsEnqueue commandsEnqueue,
                                         StateUpdate<Scene> stateUpdate) {
        RelativeUrl currentUrl = state.effectiveUrl();
        if (currentUrl == null) {
            return;
        }

        RelativeUrl updatedUrl = withQueryParameter(currentUrl, update.name(), update.value());
        Lookup lookup = LookupFactory.create(savedContext, commandsEnqueue);
        lookup.publish(AutoAddressBarSyncComponent.SET_PATH,
                new AutoAddressBarSyncComponent.PathUpdate(updatedUrl, PUSH_URL_ONLY));
        stateUpdate.applyStateTransformation(s -> s.withEffectiveUrl(updatedUrl));
    }

    private static RelativeUrl withQueryParameter(RelativeUrl url, String name, String value) {
        List<Query.Parameter> parameters = new ArrayList<>(url.query().parameters().size() + 1);
        boolean replaced = false;
        for (Query.Parameter parameter : url.query().parameters()) {
            if (parameter.name().equals(name)) {
                parameters.add(new Query.Parameter(name, value));
                replaced = true;
            } else {
                parameters.add(parameter);
            }
        }
        if (!replaced) {
            parameters.add(new Query.Parameter(name, value));
        }
        return new RelativeUrl(url.path(), new Query(parameters), url.fragment());
    }

    /**
     * Handle SET_PRIMARY event: replace the routed contract.
     */
    @SuppressWarnings("unchecked")
    private void handleSetPrimary(Scene state, Class contractClass,
                                  StateUpdate<Scene> stateUpdate,
                                  CommandsEnqueue commandsEnqueue) {
        // Check if already the routed contract
        if (state.routedContract() != null && state.routedContract().getClass().equals(contractClass)) {
            return;
        }

        // Destroy old routed contract
        if (state.routedRuntime() != null) {
            state.routedRuntime().destroy();
        }

        // Resolve and instantiate the new contract.
        // stripQueryParams=true: SET_PRIMARY navigates to a different contract class,
        // stale query state (e.g. ?p=2 from Posts) must not carry over to the new contract.
        ContractRuntime newRuntime = resolveAndInstantiate(state, contractClass, commandsEnqueue, true);
        if (newRuntime == null) {
            return;
        }

        // Update URL to reflect the new routed contract's route
        Composition composition = state.composition();
        RelativeUrl targetUrl = null;
        if (composition != null && composition.router() != null) {
            Class<? extends ViewContract> typedContractClass = (Class<? extends ViewContract>) contractClass;
            String route = composition.router()
                .findRoutePattern(typedContractClass)
                .orElse(null);
            if (route != null) {
                Lookup lookup = LookupFactory.create(savedContext, commandsEnqueue);
                // Explicit Query.EMPTY: SET_PRIMARY navigates to a different contract,
                // so any stale query state must not carry over to the new route.
                targetUrl = new RelativeUrl(Path.of(route), Query.EMPTY, Fragment.EMPTY);
                lookup.publish(AutoAddressBarSyncComponent.SET_PATH,
                               new AutoAddressBarSyncComponent.PathUpdate(targetUrl, PUSH_URL_ONLY));
            }
        }

        // SET_PRIMARY is a fresh navigation — clear any pending inline return target
        // so a subsequent ACTION_SUCCESS does not bounce the user back to a stale view.
        final RelativeUrl effectiveUrl = targetUrl;
        stateUpdate.applyStateTransformation(s ->
                effectiveUrl != null
                        ? s.withRoutedRuntime(newRuntime).clearInlineReturnTarget().withEffectiveUrl(effectiveUrl)
                        : s.withRoutedRuntime(newRuntime).clearInlineReturnTarget());
    }

    /**
     * Resolve factory, create lookup, instantiate contract, register handlers.
     */
    @SuppressWarnings("unchecked")
    private ContractRuntime resolveAndInstantiate(Scene state, Class contractClass,
                                                  CommandsEnqueue commandsEnqueue,
                                                  boolean stripQueryParams) {
        return resolveAndInstantiate(state, contractClass, commandsEnqueue, stripQueryParams, Map.of());
    }

    /**
     * Resolve factory, create lookup, instantiate contract, register handlers.
     */
    @SuppressWarnings("unchecked")
    private ContractRuntime resolveAndInstantiate(Scene state, Class contractClass,
                                                  CommandsEnqueue commandsEnqueue,
                                                  boolean stripQueryParams,
                                                  Map<String, Object> showData) {
        // Get factory from scene lazy factories
        Function<Lookup, ViewContract> factory = state.getFactory(contractClass);
        if (factory == null) {
            // Check composition registry
            Composition composition = state.composition();
            if (composition != null) {
                factory = composition.contracts().contractFactory(contractClass);
            }
        }

        if (factory == null) {
            return null;
        }

        // Create lookup with context enrichment.
        // When stripQueryParams=true (SET_PRIMARY path): strip stale query params so a
        // newly-routed contract starts clean (e.g. navigating from /posts?p=2 to Comments
        // must not carry over p=2).
        // When stripQueryParams=false (ACTION_SUCCESS refresh path): preserve query params
        // so pagination/sort/filter state survives an in-place refresh of the same contract.
        ComponentContext base = stripQueryParams
            ? savedContext.withoutStringPrefix(ContextKeys.URL_QUERY.baseKey() + ".")
            : savedContext;
        ComponentContext showContext = base
            .with(ContextKeys.CONTRACT_CLASS, contractClass)
            .with(ContextKeys.IS_ACTIVE_CONTRACT, true)
            .with(ContextKeys.SCENE, state);

        if (showData != null && !showData.isEmpty()) {
            showContext = showContext.with(ContextKeys.SHOW_DATA, showData);
        }

        return ContractRuntime.instantiate(contractClass, factory, showContext, commandsEnqueue);
    }

    /**
     * Handle ACTION_SUCCESS: refresh the routed contract, or restore the previous
     * routed contract if the current routed runtime is an inline replacement.
     * <p>
     * Restore path: when an inline form (e.g., create/edit replacing a list inline)
     * completes its action, navigate back to the captured previous routed contract,
     * preserving the query state and fragment that were active at SHOW time.
     * <p>
     * Refresh path (existing behaviour): any successful action — from the routed
     * contract or from a layer — may have modified data visible in the routed view.
     * Re-instantiate the routed contract, preserving its query state.
     */
    private void handleActionSuccess(Scene state,
                                     ActionResult result,
                                     CommandsEnqueue commandsEnqueue,
                                     StateUpdate<Scene> stateUpdate) {
        Class<? extends ViewContract> contractClass = result.contractClass();
        if (contractClass == null) {
            return;
        }
        if (state.routedRuntime() == null) {
            return;
        }

        Scene.InlineReturnTarget returnTarget = state.inlineReturnTarget();
        if (returnTarget != null
                && state.routedRuntime().contractClass().equals(contractClass)) {
            restoreInlineReturn(state, returnTarget, commandsEnqueue, stateUpdate);
            return;
        }

        // In-place refresh — same contract class, preserve query state.
        Class routedClass = state.routedRuntime().contractClass();
        state.routedRuntime().destroy();
        ContractRuntime refreshed = resolveAndInstantiate(state, routedClass, commandsEnqueue, false);
        if (refreshed != null) {
            stateUpdate.applyStateTransformation(s -> s.withRoutedRuntime(refreshed));
        }
    }

    private void restoreInlineReturn(Scene state,
                                     Scene.InlineReturnTarget target,
                                     CommandsEnqueue commandsEnqueue,
                                     StateUpdate<Scene> stateUpdate) {
        state.routedRuntime().destroy();
        // stripQueryParams=true: we'll set the canonical URL state from the captured
        // return target rather than relying on whatever savedContext currently holds.
        ContractRuntime restored = resolveAndInstantiate(state, target.contractClass(),
                commandsEnqueue, true);
        if (restored == null) {
            return;
        }

        Lookup lookup = LookupFactory.create(savedContext, commandsEnqueue);
        RelativeUrl url = new RelativeUrl(Path.of(target.route()), target.query(), target.fragment());
        lookup.publish(AutoAddressBarSyncComponent.SET_PATH,
                       new AutoAddressBarSyncComponent.PathUpdate(url, PUSH_URL_ONLY));

        stateUpdate.applyStateTransformation(s ->
                s.withRoutedRuntime(restored).clearInlineReturnTarget().withEffectiveUrl(url));
    }
}
