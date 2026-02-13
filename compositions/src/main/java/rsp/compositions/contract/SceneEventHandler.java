package rsp.compositions.contract;

import rsp.component.*;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Slot;
import rsp.compositions.composition.ViewPlacement;
import rsp.compositions.routing.AutoAddressBarSyncComponent;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static rsp.compositions.contract.EventKeys.*;
import static rsp.compositions.routing.AutoAddressBarSyncComponent.PathUpdateMode.RE_RENDER_SUBTREE;

/**
 * Registers and handles Scene lifecycle events for the base layer (primary + sidebars).
 * <p>
 * Events handled:
 * <ul>
 *   <li>SET_PRIMARY - Replace the primary contract</li>
 *   <li>ACTION_SUCCESS - Refresh primary contract in place (same contract case)</li>
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

        // ACTION_SUCCESS handler: primary contract refresh in place
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
     * Resolve factory, resolve slot, create lookup, instantiate contract, register handlers.
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
            return null;
        }

        // Create lookup with context enrichment, marking contract as active
        ComponentContext showContext = savedContext
            .with(ContextKeys.CONTRACT_CLASS, contractClass)
            .with(ContextKeys.IS_ACTIVE_CONTRACT, true)
            .with(ContextKeys.SCENE, state);

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
     * Handle SET_PRIMARY event: replace the primary contract.
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
                Lookup lookup = LookupFactory.create(savedContext, commandsEnqueue);
                lookup.publish(AutoAddressBarSyncComponent.SET_PATH,
                               new AutoAddressBarSyncComponent.PathUpdate(route, RE_RENDER_SUBTREE));
            }
        }

        stateUpdate.applyStateTransformation(s ->
                s.withPrimaryContract(resolved.contract())
        );
    }

    /**
     * Handle ACTION_SUCCESS: always refresh the primary contract.
     * <p>
     * Any successful action (whether from the primary itself or from an overlay layer)
     * may have modified data visible in the primary view. LayerComponent handles
     * closing the overlay; this handler ensures the primary list reflects current data.
     */
    @SuppressWarnings("unchecked")
    private void handleActionSuccess(Scene state,
                                     ActionResult result,
                                     CommandsEnqueue commandsEnqueue,
                                     StateUpdate<Scene> stateUpdate) {
        Class<? extends ViewContract> contractClass = result.contractClass();
        if (contractClass == null) {
            return;
        }

        // Always refresh primary — the action may have modified data visible in the primary view
        state.primaryContract().onDestroy();
        Class primaryClass = state.primaryContract().getClass();
        ResolvedContract resolved = resolveAndInstantiate(state, primaryClass, null, commandsEnqueue);
        if (resolved != null) {
            stateUpdate.applyStateTransformation(s -> s.withPrimaryContract(resolved.contract()));
        }
    }
}
