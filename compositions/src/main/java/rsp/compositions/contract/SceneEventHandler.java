package rsp.compositions.contract;

import rsp.component.*;
import rsp.compositions.composition.Composition;
import rsp.compositions.layout.PlacementDecision;
import rsp.server.http.RelativeUrl;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static rsp.compositions.contract.ActionBindings.ShowPayload;
import static rsp.compositions.contract.EventKeys.*;

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
        final SceneNavigator navigator = new SceneNavigator(savedContext, commandsEnqueue);
        final Scene.InlineReturnTarget returnTarget = navigator.captureInlineReturnTarget(state);

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
        RelativeUrl targetUrl = navigator.pushInlineUrl(state, contractClass, payload.data());

        stateUpdate.applyStateTransformation(s -> {
            Scene next = s.withRoutedRuntime(newRuntime);
            if (targetUrl != null) {
                next = next.withEffectiveUrl(targetUrl);
            }
            return returnTarget != null ? next.withInlineReturnTarget(returnTarget) : next;
        });
    }

    private void handleSceneQueryUpdated(Scene state,
                                         EventKeys.SceneQueryUpdate update,
                                         CommandsEnqueue commandsEnqueue,
                                         StateUpdate<Scene> stateUpdate) {
        RelativeUrl updatedUrl = new SceneNavigator(savedContext, commandsEnqueue)
                .pushSceneQueryUpdate(state.effectiveUrl(), update);
        if (updatedUrl == null) {
            return;
        }
        stateUpdate.applyStateTransformation(s -> s.withEffectiveUrl(updatedUrl));
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

        // Update URL to reflect the new routed contract's route.
        // SET_PRIMARY is a fresh primary-contract selection, so the navigator
        // uses an empty query and fragment for the target URL.
        Class<? extends ViewContract> typedContractClass = (Class<? extends ViewContract>) contractClass;
        RelativeUrl targetUrl = new SceneNavigator(savedContext, commandsEnqueue)
                .pushPrimaryUrl(state, typedContractClass);

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

        RelativeUrl url = new SceneNavigator(savedContext, commandsEnqueue)
                .pushReturnUrl(target);

        stateUpdate.applyStateTransformation(s ->
                s.withRoutedRuntime(restored).clearInlineReturnTarget().withEffectiveUrl(url));
    }
}
