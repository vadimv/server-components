package rsp.compositions.contract;

import rsp.component.*;
import rsp.component.definitions.AddressBarSyncComponent;
import rsp.compositions.composition.Composition;
import rsp.compositions.layout.PlacementDecision;
import rsp.compositions.routing.Router;
import rsp.server.http.RelativeUrl;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
                                 StateUpdater<Scene> stateUpdate) {
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

        subscriber.addEventHandler(SCENE_TITLE_UPDATED, (eventName, update) -> {
            handleSceneTitleUpdated(state, update, stateUpdate);
        }, false);

        subscriber.addEventHandler(AddressBarSyncComponent.HISTORY_ENTRY_CHANGED, (eventName, url) ->
                handleBrowserHistory(state, url, commandsEnqueue, stateUpdate), false);
    }

    private void handleShow(Scene state,
                            ShowPayload payload,
                            StateUpdater<Scene> stateUpdate,
                            CommandsEnqueue commandsEnqueue) {
        Class<? extends Contract> contractClass = payload.contractClass();
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
                                  StateUpdater<Scene> stateUpdate,
                                  CommandsEnqueue commandsEnqueue) {
        Class<? extends Contract> contractClass = payload.contractClass();

        if (state.isRouted(contractClass)) {
            return;
        }

        // Capture the current routed contract as a return target before destroying it,
        // so a later ACTION_SUCCESS from the inline form can restore the previous view
        // (e.g., Save/Cancel on an inline edit form returns to the list).
        final SceneNavigator navigator = new SceneNavigator(savedContext, commandsEnqueue);
        final Scene.InlineReturnTarget returnTarget = navigator.captureInlineReturnTarget(state);

        ContractDescriptor descriptor = describeContract(state, contractClass, payload.data());
        if (descriptor == null) {
            return;
        }

        // Update the URL bar to reflect the now-routed inline contract (e.g. /comments/3).
        // The Router's pattern is the source of truth for URL shape; we substitute path
        // parameters from the SHOW payload data and preserve the current query state.
        RelativeUrl targetUrl = navigator.pushInlineUrl(state, contractClass, payload.data());

        stateUpdate.applyStateTransformation(s -> {
            Scene next = s.withRoutedDescriptor(descriptor);
            if (targetUrl != null) {
                next = next.withEffectiveUrl(targetUrl);
            }
            return returnTarget != null ? next.withInlineReturnTarget(returnTarget) : next;
        });
    }

    private void handleSceneQueryUpdated(Scene state,
                                         EventKeys.SceneQueryUpdate update,
                                         CommandsEnqueue commandsEnqueue,
                                         StateUpdater<Scene> stateUpdate) {
        RelativeUrl updatedUrl = new SceneNavigator(savedContext, commandsEnqueue)
                .pushSceneQueryUpdate(state.effectiveUrl(), update);
        if (updatedUrl == null) {
            return;
        }
        stateUpdate.applyStateTransformation(s -> s.withEffectiveUrl(updatedUrl));
    }

    private void handleSceneTitleUpdated(Scene state,
                                         EventKeys.SceneTitleUpdate update,
                                         StateUpdater<Scene> stateUpdate) {
        ContractDescriptor routed = state.routedDescriptor();
        boolean isPrimary = routed != null && routed.instanceId() == update.descriptorId();
        ContractDescriptor autoOpen = state.autoOpen() == null
                ? null
                : state.preActivatedDescriptor(state.autoOpen().contractClass());
        boolean isAutoOpenOverlay = autoOpen != null && autoOpen.instanceId() == update.descriptorId();
        if ((!isPrimary && !isAutoOpenOverlay) || state.pageTitle().equals(update.title())) {
            return;
        }
        stateUpdate.applyStateTransformation(s -> s.withPageTitle(update.title()));
    }

    private void handleBrowserHistory(Scene state,
                                      RelativeUrl targetUrl,
                                      CommandsEnqueue commandsEnqueue,
                                      StateUpdater<Scene> stateUpdate) {
        if (state.effectiveUrl() == null) {
            return;
        }

        if (state.effectiveUrl().equals(targetUrl)) {
            return;
        }

        Composition composition = state.composition();
        if (composition == null || composition.router() == null) {
            return;
        }

        Optional<Router.RouteMatch> match = composition.router().match(targetUrl.path());
        if (match.isEmpty()) {
            return;
        }

        Class<? extends Contract> targetClass = match.get().contractClass();
        if (state.isRouted(targetClass)) {
            stateUpdate.applyStateTransformation(s -> s.withEffectiveUrl(targetUrl));
            return;
        }

        ContractDescriptor descriptor = describeContractForUrl(state, targetClass);
        if (descriptor == null) {
            return;
        }

        stateUpdate.applyStateTransformation(s ->
                s.withRoutedDescriptor(descriptor)
                        .clearInlineReturnTarget()
                        .withEffectiveUrl(targetUrl));
    }

    /**
     * Handle SET_PRIMARY event: replace the routed contract.
     */
    @SuppressWarnings("unchecked")
    private void handleSetPrimary(Scene state, Class contractClass,
                                  StateUpdater<Scene> stateUpdate,
                                  CommandsEnqueue commandsEnqueue) {
        // Check if already the routed contract
        if (state.routedDescriptor() != null && state.routedDescriptor().contractClass().equals(contractClass)) {
            return;
        }

        ContractDescriptor descriptor = describeContract(state, contractClass, Map.of());
        if (descriptor == null) {
            return;
        }

        // Update URL to reflect the new routed contract's route.
        // SET_PRIMARY is a fresh primary-contract selection, so the navigator
        // uses an empty query and fragment for the target URL.
        Class<? extends Contract> typedContractClass = (Class<? extends Contract>) contractClass;
        RelativeUrl targetUrl = new SceneNavigator(savedContext, commandsEnqueue)
                .pushPrimaryUrl(state, typedContractClass);

        // SET_PRIMARY is a fresh navigation — clear any pending inline return target
        // so a subsequent ACTION_SUCCESS does not bounce the user back to a stale view.
        final RelativeUrl effectiveUrl = targetUrl;
        stateUpdate.applyStateTransformation(s ->
                effectiveUrl != null
                        ? s.withRoutedDescriptor(descriptor).clearInlineReturnTarget().withEffectiveUrl(effectiveUrl)
                        : s.withRoutedDescriptor(descriptor).clearInlineReturnTarget());
    }

    /**
     * Resolve a fresh descriptor for a contract that will mount in the tree.
     */
    @SuppressWarnings("unchecked")
    private ContractDescriptor describeContract(Scene state, Class contractClass) {
        return describeContract(state, contractClass, Map.of());
    }

    /**
     * Select a fresh component-owned contract descriptor.
     */
    @SuppressWarnings("unchecked")
    private ContractDescriptor describeContract(Scene state, Class contractClass,
                                                  Map<String, Object> showData) {
        Composition composition = state.composition();
        if (composition == null || !composition.contracts().hasBinding(contractClass)) {
            return null;
        }
        return ContractDescriptor.forContract(contractClass, showData);
    }

    private ContractDescriptor describeContractForUrl(Scene state,
                                                       Class<? extends Contract> contractClass) {
        if (state.composition() == null
                || !state.composition().contracts().hasBinding(contractClass)) {
            return null;
        }
        return ContractDescriptor.forContract(contractClass, Map.of());
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
                                     StateUpdater<Scene> stateUpdate) {
        Class<? extends Contract> contractClass = result.contractClass();
        if (contractClass == null) {
            return;
        }
        if (state.routedDescriptor() == null) {
            return;
        }

        Scene.InlineReturnTarget returnTarget = state.inlineReturnTarget();
        if (returnTarget != null
                && state.routedDescriptor().contractClass().equals(contractClass)) {
            restoreInlineReturn(state, returnTarget, commandsEnqueue, stateUpdate);
            return;
        }

        // In-place refresh — same contract class, preserve query state.
        Class routedClass = state.routedDescriptor().contractClass();
        ContractDescriptor refreshed = describeContract(state, routedClass);
        if (refreshed != null) {
            stateUpdate.applyStateTransformation(s -> s.withRoutedDescriptor(refreshed));
        }
    }

    private void restoreInlineReturn(Scene state,
                                     Scene.InlineReturnTarget target,
                                     CommandsEnqueue commandsEnqueue,
                                     StateUpdater<Scene> stateUpdate) {
        ContractDescriptor restored = describeContract(state, target.contractClass());
        if (restored == null) {
            return;
        }

        RelativeUrl url = new SceneNavigator(savedContext, commandsEnqueue)
                .pushReturnUrl(target);

        stateUpdate.applyStateTransformation(s ->
                s.withRoutedDescriptor(restored).clearInlineReturnTarget().withEffectiveUrl(url));
    }
}
