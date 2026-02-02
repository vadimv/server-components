package rsp.compositions.contract;

import rsp.component.EventKey;
import rsp.component.definitions.ContextStateComponent;

import java.util.Map;
import java.util.Set;

import static rsp.compositions.contract.ActionBindings.*;

public final class EventKeys {
    private EventKeys() {}

    // ===== SHOW/HIDE EVENTS (Scene-level contract lifecycle) =====

    /**
     * Show a contract (on-demand instantiation).
     * Emitted by: Contracts (via ACTION binding translation)
     * Handled by: SceneComponent (instantiates contract, adds to scene)
     * Payload: ShowPayload with contract class and data
     * <p>
     * Data flow:
     * <ol>
     *   <li>View emits ACTION("edit", {id: "123"})</li>
     *   <li>Contract translates via actionBindings() to SHOW(EditContract.class, {id: "123"})</li>
     *   <li>SceneComponent receives SHOW, instantiates contract on-demand</li>
     * </ol>
     */
    public static final EventKey.SimpleKey<ShowPayload> SHOW =
            new EventKey.SimpleKey<>("show", ShowPayload.class);

    public static final EventKey.SimpleKey<Class> SET_PRIMARY =
            new EventKey.SimpleKey<>("setPrimary", Class.class);


    /**
     * Hide a contract (destroy instance).
     * Emitted by: Views (close button), Contracts (after save/delete)
     * Handled by: SceneComponent (calls onDestroy, removes from scene)
     * Payload: Contract class to hide (always explicit about what to close)
     * <p>
     * Unlike CLOSE_OVERLAY which is generic, HIDE always specifies which
     * contract to close. This supports multiple overlays being shown.
     */
    @SuppressWarnings("unchecked")
    public static final EventKey.SimpleKey<Class<? extends ViewContract>> HIDE =
            new EventKey.SimpleKey<>("hide",
                    (Class<Class<? extends ViewContract>>) (Class<?>) Class.class);


    /**
     * State updated event for any context parameter.
     * Dynamic key: "stateUpdated.*" for "stateUpdated.p", "stateUpdated.sort", etc.
     * Emitted by: DefaultListView (pagination, sorting)
     * Handled by: AddressBarSyncComponent, AutoAddressBarSyncComponent
     * Payload: ContextStateComponent.ContextValue.StringValue
     */
    public static final EventKey.DynamicKey<ContextStateComponent.ContextValue> STATE_UPDATED =
            new EventKey.DynamicKey<>("stateUpdated", ContextStateComponent.ContextValue.class);

    /**
     * Navigate to a URL path (SPA-style navigation).
     * Emitted by: Framework (SceneComponent) after ACTION_SUCCESS
     * Handled by: AutoAddressBarSyncComponent (uses PushHistory for internal paths)
     * Payload: The URL path to navigate to
     */
    public static final EventKey.SimpleKey<String> NAVIGATE =
            new EventKey.SimpleKey<>("navigate", String.class);


    /**
     * Action succeeded (generic success event).
     * Emitted by: Contracts (EditViewContract, FormViewContract) after successful operations
     * Handled by: Framework (SceneComponent) which derives navigation from composition config
     * Payload: ActionResult containing contract class and operation type
     * <p>
     * This follows the CountersMainComponent pattern:
     * - Contracts emit INTENT (action type only, no routes)
     * - Framework derives NAVIGATION from composition/router configuration
     * <p>
     * Framework behavior based on placement:
     * <ul>
     *   <li>OVERLAY → HIDE + legacy event + REFRESH_LIST</li>
     *   <li>PRIMARY (same contract) → scene rebuild (refresh in place)</li>
     *   <li>PRIMARY (different contract) → navigate to list route (derived from Router)</li>
     * </ul>
     * <p>
     * Example flow:
     * <ol>
     *   <li>Contract saves entity, emits ACTION_SUCCESS(SAVE)</li>
     *   <li>SceneComponent receives event, checks contract placement</li>
     *   <li>Framework derives list route from composition's Router</li>
     *   <li>Framework navigates to derived route</li>
     * </ol>
     */
    public static final EventKey.SimpleKey<ActionResult> ACTION_SUCCESS =
            new EventKey.SimpleKey<>("action.success", ActionResult.class);

    /**
     * Action result containing operation type.
     * <p>
     * Framework derives navigation from composition configuration:
     * <ul>
     *   <li>OVERLAY → HIDE + REFRESH_LIST</li>
     *   <li>PRIMARY (same contract) → scene rebuild (refresh in place)</li>
     *   <li>PRIMARY (different contract) → navigate to list route (derived from Router)</li>
     * </ul>
     * <p>
     * This follows the CountersMainComponent pattern: contracts emit INTENT (action type),
     * framework derives NAVIGATION from composition/router configuration.
     *
     * @param contractClass The class of the contract that performed the action
     * @param type The type of action that was performed (SAVE, DELETE, CANCEL)
     */
    public record ActionResult(
        Class<? extends ViewContract> contractClass,
        ActionType type
    ) {}

    /**
     * Type of action performed by a contract.
     * Used by framework to determine appropriate UI response (e.g., which legacy event to emit).
     */
    public enum ActionType {
        /** Entity save operation */
        SAVE,
        /** Entity delete operation */
        DELETE,
        /** User cancelled operation */
        CANCEL
    }
}
