package rsp.compositions.contract;

import rsp.component.EventKey;
import rsp.component.definitions.ContextStateComponent;

import java.util.Objects;

import static rsp.compositions.contract.ActionBindings.*;

public final class EventKeys {
    private EventKeys() {}

    /**
     * Show a contract (on-demand instantiation).
     * Emitted by: Contracts (via ACTION binding translation)
     * Handled by: SceneEventHandler (selects a descriptor in scene state)
     * Payload: ShowPayload with contract class and data
     * <p>
     * Data flow:
     * <ol>
     *   <li>View emits ACTION("edit", {id: "123"})</li>
     *   <li>Contract translates via actionBindings() to SHOW(EditContract.class, {id: "123"})</li>
     *   <li>SceneEventHandler receives SHOW and selects a descriptor on-demand</li>
     * </ol>
     */
    public static final EventKey.SimpleKey<ShowPayload> SHOW =
            new EventKey.SimpleKey<>("show", ShowPayload.class);

    /**
     * Internal layer show event emitted after placement resolution.
     * <p>
     * Application contracts should continue to publish {@link #SHOW}. The scene
     * layer decides whether the target should replace inline content or open a
     * layer, then forwards layer-bound targets with this event.
     */
    public static final EventKey.SimpleKey<ShowPayload> SHOW_LAYER =
            new EventKey.SimpleKey<>("show.layer", ShowPayload.class);

    public static final EventKey.SimpleKey<Class> SET_PRIMARY =
            new EventKey.SimpleKey<>("setPrimary", Class.class);


    /**
     * Hide a contract (destroy instance).
     * Emitted by: Views (close button), Contracts (after save/delete)
     * Handled by: LayerComponent, which removes the descriptor and unmounts
     * its DirectContractHost.
     * Payload: Contract class to hide (always explicit about what to close)
     * <p>
     * Unlike CLOSE_OVERLAY which is generic, HIDE always specifies which
     * contract to close. This supports multiple overlays being shown.
     */
    @SuppressWarnings("unchecked")
    public static final EventKey.SimpleKey<Class<? extends Contract>> HIDE =
            new EventKey.SimpleKey<>("hide",
                    (Class<Class<? extends Contract>>) (Class<?>) Class.class);


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
     * Scene-local query update for transitions that pushed browser history
     * without rebuilding the root route shell.
     * <p>
     * Emitted by contracts when {@link ContextKeys#SCENE} carries an effective
     * URL. Handled by {@link SceneEventHandler}, which updates that effective
     * URL and pushes browser history while preserving companion runtimes.
     */
    public static final EventKey.SimpleKey<SceneQueryUpdate> SCENE_QUERY_UPDATED =
            new EventKey.SimpleKey<>("scene.query.updated", SceneQueryUpdate.class);

    /**
     * Reports the title produced by a mounted contract runtime. Scene state uses
     * this only for the matching primary descriptor, keeping title resolution
     * inside the component tree without letting companions overwrite it.
     */
    public static final EventKey.SimpleKey<SceneTitleUpdate> SCENE_TITLE_UPDATED =
            new EventKey.SimpleKey<>("scene.title.updated", SceneTitleUpdate.class);

    /**
     * Announces that the component tree mounted the currently routed contract.
     * Consumers that need a live contract, such as an agent sidebar, keep their
     * own local reference rather than reading one from Scene state.
     */
    public static final EventKey.SimpleKey<MountedPrimaryContract> PRIMARY_CONTRACT_MOUNTED =
            new EventKey.SimpleKey<>("scene.primary.contract.mounted", MountedPrimaryContract.class);

    /**
     * Query parameter update payload for scene-local URL state.
     *
     * @param name query parameter name
     * @param value query parameter value
     */
    public record SceneQueryUpdate(String name, String value) {
        public SceneQueryUpdate {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    public record SceneTitleUpdate(long descriptorId, String title) {
        public SceneTitleUpdate {
            if (descriptorId < 1) {
                throw new IllegalArgumentException("descriptorId must be positive");
            }
            Objects.requireNonNull(title, "title");
        }
    }

    public record MountedPrimaryContract(long descriptorId, Contract contract) {
        public MountedPrimaryContract {
            if (descriptorId < 1) {
                throw new IllegalArgumentException("descriptorId must be positive");
            }
            Objects.requireNonNull(contract, "contract");
        }
    }


    /**
     * Action succeeded (data event).
     * Emitted by: form contract components after successful operations
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
        Class<? extends Contract> contractClass
    ) {}

}
