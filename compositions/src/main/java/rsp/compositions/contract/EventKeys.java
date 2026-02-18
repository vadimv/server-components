package rsp.compositions.contract;

import rsp.component.EventKey;
import rsp.component.definitions.ContextStateComponent;

import static rsp.compositions.contract.ActionBindings.*;

public final class EventKeys {
    private EventKeys() {}

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
     * Action succeeded (data event).
     * Emitted by: Contracts (EditViewContract, FormViewContract) after successful operations
     * Payload: ActionResult containing contract class
     * <p>
     * This is a data event — contracts decide their own post-action behavior.
     * The framework does not impose auto-close or auto-navigate heuristics.
     * Contracts that want to close after success should publish HIDE themselves.
     */
    public static final EventKey.SimpleKey<ActionResult> ACTION_SUCCESS =
            new EventKey.SimpleKey<>("action.success", ActionResult.class);

    /**
     * Action result payload.
     *
     * @param contractClass The class of the contract that performed the action
     */
    public record ActionResult(
        Class<? extends ViewContract> contractClass
    ) {}

}
