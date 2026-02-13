package rsp.compositions.contract;

import rsp.component.*;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Slot;
import rsp.compositions.composition.ViewPlacement;
import rsp.compositions.routing.AutoAddressBarSyncComponent;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static rsp.compositions.contract.ActionBindings.ShowPayload;
import static rsp.compositions.contract.EventKeys.*;
import static rsp.compositions.routing.AutoAddressBarSyncComponent.PathUpdateMode.RE_RENDER_SUBTREE;

/**
 * Registers and handles Scene lifecycle events.
 * <p>
 * Events handled:
 * <ul>
 *   <li>SHOW - On-demand contract instantiation (lazy activation)</li>
 *   <li>HIDE - Contract destruction and cleanup</li>
 *   <li>SET_PRIMARY - Replace the primary contract</li>
 *   <li>ACTION_SUCCESS - Framework-driven navigation after contract operations</li>
 * </ul>
 */
public final class SceneEventHandler {

    private final ComponentContext savedContext;

    public SceneEventHandler(ComponentContext savedContext) {
        this.savedContext = Objects.requireNonNull(savedContext, "savedContext");
    }

    /**
     * Register all event handlers for scene lifecycle management.
     */
    public void registerHandlers(Scene state,
                                 Subscriber subscriber,
                                 CommandsEnqueue commandsEnqueue,
                                 StateUpdate<Scene> stateUpdate) {
        // SHOW handler: instantiate contract on-demand
        subscriber.addEventHandler(SHOW, (eventName, payload) -> {
            handleShowEvent(state, payload, stateUpdate, commandsEnqueue);
        }, false);

        // HIDE handler: destroy contract
        subscriber.addEventHandler(HIDE, (eventName, contractClass) -> {
            handleHideEvent(state, contractClass, stateUpdate);
        }, false);

        subscriber.addEventHandler(SET_PRIMARY, (eventName, contractClass) -> {
            handleSetPrimary(state, contractClass, stateUpdate, commandsEnqueue);
        }, false);

        // ACTION_SUCCESS handler: framework-driven navigation based on contract placement
        subscriber.addEventHandler(ACTION_SUCCESS, (eventName, result) -> {
            handleActionSuccess(state, result, commandsEnqueue, stateUpdate);
        }, false);
    }

    /**
     * Result of resolving and instantiating a contract from an event.
     */
    private record ResolvedContract(
        ViewContract contract,
        Slot targetSlot
    ) {}

    /**
     * Shared logic between SHOW and SET_PRIMARY: resolve factory, resolve slot,
     * create lookup, instantiate contract, register handlers.
     *
     * @param state           current scene
     * @param contractClass   the contract class to instantiate
     * @param showData        data from SHOW event (null for SET_PRIMARY)
     * @param commandsEnqueue for creating the lookup
     * @return the resolved contract with its slot, or null if resolution failed
     */
    @SuppressWarnings("unchecked")
    private ResolvedContract resolveAndInstantiate(
            Scene state,
            Class contractClass,
            Map<String, Object> showData,
            CommandsEnqueue commandsEnqueue) {

        // Check if already active
        if (state.findContract(contractClass) != null) {
            return null; // Already shown
        }

        // Get factory from scene
        Function<Lookup, ViewContract> factory = state.getFactory(contractClass);
        if (factory == null) {
            // Not found in factories - check composition placements
            Composition composition = state.composition();
            ViewPlacement placement = composition != null ? composition.placementFor(contractClass) : null;
            if (placement != null) {
                factory = placement.contractFactory();
            }
        }

        if (factory == null) {
            return null; // No factory available
        }

        // Resolve target slot from composition (no default assumption)
        Composition composition = state.composition();
        Slot targetSlot = null;
        if (composition != null) {
            ViewPlacement placement = composition.placementFor(contractClass);
            if (placement != null) {
                targetSlot = placement.slot();
            }
        }

        if (targetSlot == null) {
            // Fallback: if no placement found, cannot determine slot
            return null;
        }

        // Create lookup with context enrichment, marking contract as active
        // Note: Don't set IS_OVERLAY_MODE - Scene is slot-agnostic, contracts use SlotUtils if needed
        ComponentContext showContext = savedContext
            .with(ContextKeys.CONTRACT_CLASS, contractClass)
            .with(ContextKeys.IS_ACTIVE_CONTRACT, true)  // Mark as active
            .with(ContextKeys.SCENE, state);  // Add Scene for SlotUtils

        // Add SHOW_DATA only if provided (SHOW event has data, SET_PRIMARY does not)
        if (showData != null) {
            showContext = showContext.with(ContextKeys.SHOW_DATA, showData);
        }

        Lookup lookup = LookupFactory.create(showContext, commandsEnqueue);

        // Instantiate contract
        ViewContract contract = factory.apply(lookup);
        if (contract == null) {
            return null;
        }
        contract.registerHandlers();

        return new ResolvedContract(contract, targetSlot);
    }

    /**
     * Handle SHOW event: instantiate contract on-demand and add to scene.
     */
    private void handleShowEvent(Scene state, ShowPayload payload,
                                 StateUpdate<Scene> stateUpdate,
                                 CommandsEnqueue commandsEnqueue) {
        Class<? extends ViewContract> contractClass = payload.contractClass();
        Map<String, Object> data = payload.data();

        ResolvedContract resolved = resolveAndInstantiate(state, contractClass, data, commandsEnqueue);
        if (resolved == null) {
            return;
        }

        // Update state with active contract
        final Slot slot = resolved.targetSlot();
        stateUpdate.applyStateTransformation(s ->
            s.withActiveContract(slot, resolved.contract(), contractClass, data)
        );
    }

    /**
     * Handle SET_PRIMARY event: replace the primary contract.
     * Preserves original ordering: check findContract before onDestroy.
     */
    @SuppressWarnings("unchecked")
    private void handleSetPrimary(Scene state, Class contractClass,
                                  StateUpdate<Scene> stateUpdate,
                                  CommandsEnqueue commandsEnqueue) {
        // Check if already active FIRST (before destroying old primary)
        if (state.findContract(contractClass) != null) {
            return; // Already shown
        }

        if (state.primaryContract() != null) {
            state.primaryContract().onDestroy();
        }

        ResolvedContract resolved = resolveAndInstantiate(state, contractClass, null, commandsEnqueue);
        if (resolved == null) {
            return;
        }

        // Update URL to reflect the new primary contract's route
        Composition composition = state.composition();
        if (composition != null && composition.router() != null) {
            Class<? extends ViewContract> typedContractClass = (Class<? extends ViewContract>) contractClass;
            String route = composition.router()
                .findRoutePattern(typedContractClass)
                .orElse(null);
            if (route != null) {
                // To update browser URL
                Lookup lookup = LookupFactory.create(savedContext, commandsEnqueue);
                // Re-render to reset query params (e.g., pagination) when switching primary via Explorer.
                lookup.publish(AutoAddressBarSyncComponent.SET_PATH,
                               new AutoAddressBarSyncComponent.PathUpdate(route, RE_RENDER_SUBTREE));
            }
        }

        stateUpdate.applyStateTransformation(s ->
                s.withPrimaryContract(resolved.contract())
        );
    }

    /**
     * Handle HIDE event: destroy contract and remove from scene.
     */
    private void handleHideEvent(Scene state,
                                 Class<? extends ViewContract> contractClass,
                                 StateUpdate<Scene> stateUpdate) {
        // Find the contract
        ViewContract contract = state.findContract(contractClass);
        if (contract == null) {
            return; // Not active
        }

        // Call cleanup hook
        contract.onDestroy();

        // Update state to remove contract
        stateUpdate.applyStateTransformation(s -> s.withContractClosed(contractClass));
    }

    /**
     * Handle ACTION_SUCCESS event: framework-driven navigation based on contract placement.
     * <p>
     * Framework behavior based on placement:
     * <ul>
     *   <li>OVERLAY → HIDE (close overlay)</li>
     *   <li>PRIMARY (same contract) → scene rebuild (refresh in place)</li>
     * </ul>
     */
    private void handleActionSuccess(Scene state,
                                     ActionResult result,
                                     CommandsEnqueue commandsEnqueue,
                                     StateUpdate<Scene> stateUpdate) {
        // Get contract class from the action result
        Class<? extends ViewContract> contractClass = result.contractClass();
        if (contractClass == null) {
            return; // No contract class in result
        }

        // Create lookup for publishing events
        Lookup lookup = LookupFactory.create(savedContext, commandsEnqueue);

        // Determine behavior based on placement
        if (SlotUtils.isInOverlay(contractClass, state)) {
            // OVERLAY behavior:
            // If this overlay was auto-opened via direct URL, navigate back to parent route.
            // Otherwise just close the overlay.
            if (state.autoOpen() != null
                && state.autoOpen().contractClass().equals(contractClass)) {
                String parentRoute = RouteUtils.buildParentRoute(state.autoOpen().routePattern(), lookup);
                lookup.publish(AutoAddressBarSyncComponent.SET_PATH,
                               new AutoAddressBarSyncComponent.PathUpdate(parentRoute, RE_RENDER_SUBTREE));
                return;
            }
            lookup.publish(HIDE, contractClass);

        } else {
            // PRIMARY behavior: derive navigation from composition
            // Case 1: Same contract as primary (e.g., bulk delete on list) → refresh in place
            if (contractClass.equals(state.primaryContract().getClass())) {
                // Destroy old primary and recreate it with fresh data, preserving sidebars
                state.primaryContract().onDestroy();
                ResolvedContract resolved = resolveAndInstantiate(state, contractClass, null, commandsEnqueue);
                if (resolved != null) {
                    stateUpdate.applyStateTransformation(s -> s.withPrimaryContract(resolved.contract()));
                }
            }
        }
    }
}
