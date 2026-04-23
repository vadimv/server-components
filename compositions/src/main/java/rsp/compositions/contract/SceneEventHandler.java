package rsp.compositions.contract;

import rsp.component.*;
import rsp.compositions.composition.Composition;
import rsp.compositions.routing.AutoAddressBarSyncComponent;

import java.util.Objects;
import java.util.function.Function;

import static rsp.compositions.contract.EventKeys.*;
import static rsp.compositions.routing.AutoAddressBarSyncComponent.PathUpdateMode.UPDATE_PATH_ONLY;

/**
 * Registers and handles Scene lifecycle events for the base layer (routed + companions).
 * <p>
 * Events handled:
 * <ul>
 *   <li>SET_PRIMARY - Replace the routed contract</li>
 *   <li>ACTION_SUCCESS - Refresh routed contract in place (data may have changed)</li>
 * </ul>
 * <p>
 * Overlay events (SHOW, HIDE, overlay ACTION_SUCCESS) are handled by LayerComponent.
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
        subscriber.addEventHandler(SET_PRIMARY, (eventName, contractClass) -> {
            handleSetPrimary(state, contractClass, stateUpdate, commandsEnqueue);
        }, false);

        // ACTION_SUCCESS handler: refresh routed contract in place
        subscriber.addEventHandler(ACTION_SUCCESS, (eventName, result) -> {
            handleActionSuccess(state, result, commandsEnqueue, stateUpdate);
        }, false);
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
        if (state.routedContract() != null) {
            state.routedContract().onDestroy();
        }

        // Resolve and instantiate the new contract.
        // stripQueryParams=true: SET_PRIMARY navigates to a different contract class,
        // stale query state (e.g. ?p=2 from Posts) must not carry over to the new contract.
        ViewContract newContract = resolveAndInstantiate(state, contractClass, commandsEnqueue, true);
        if (newContract == null) {
            return;
        }

        // Update URL to reflect the new routed contract's route
        Composition composition = state.composition();
        if (composition != null && composition.router() != null) {
            Class<? extends ViewContract> typedContractClass = (Class<? extends ViewContract>) contractClass;
            String route = composition.router()
                .findRoutePattern(typedContractClass)
                .orElse(null);
            if (route != null) {
                Lookup lookup = LookupFactory.create(savedContext, commandsEnqueue);
                lookup.publish(AutoAddressBarSyncComponent.SET_PATH,
                               new AutoAddressBarSyncComponent.PathUpdate(route, UPDATE_PATH_ONLY));
            }
        }

        stateUpdate.applyStateTransformation(s -> s.withRoutedContract(newContract));
    }

    /**
     * Resolve factory, create lookup, instantiate contract, register handlers.
     */
    @SuppressWarnings("unchecked")
    private ViewContract resolveAndInstantiate(Scene state, Class contractClass,
                                               CommandsEnqueue commandsEnqueue,
                                               boolean stripQueryParams) {
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

        Lookup lookup = LookupFactory.create(showContext, commandsEnqueue);

        // Instantiate contract
        ViewContract contract = factory.apply(lookup);
        if (contract == null) {
            return null;
        }
        contract.registerHandlers();

        return contract;
    }

    /**
     * Handle ACTION_SUCCESS: always refresh the routed contract.
     * <p>
     * Any successful action (whether from the routed contract itself or from a layer)
     * may have modified data visible in the routed view. LayerComponent handles
     * closing the overlay; this handler ensures the routed list reflects current data.
     */
    private void handleActionSuccess(Scene state,
                                     ActionResult result,
                                     CommandsEnqueue commandsEnqueue,
                                     StateUpdate<Scene> stateUpdate) {
        Class<? extends ViewContract> contractClass = result.contractClass();
        if (contractClass == null) {
            return;
        }

        // Always refresh routed contract — the action may have modified visible data.
        // stripQueryParams=false: same contract class, preserve pagination/sort/filter
        // query state across the refresh (e.g. stay on ?p=2 after Save/Cancel).
        state.routedContract().onDestroy();
        Class routedClass = state.routedContract().getClass();
        ViewContract refreshed = resolveAndInstantiate(state, routedClass, commandsEnqueue, false);
        if (refreshed != null) {
            stateUpdate.applyStateTransformation(s -> s.withRoutedContract(refreshed));
        }
    }
}
