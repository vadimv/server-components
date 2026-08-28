package rsp.compositions.block;

import rsp.component.EventKey;
import rsp.component.definitions.ContextStateComponent;

import java.util.Objects;

import static rsp.compositions.block.ActionBindings.*;

public final class EventKeys {
    private EventKeys() {}

    /**
     * Show a block (on-demand instantiation).
     * Emitted by: Blocks (via ACTION binding translation)
     * Handled by: SceneEventHandler (selects a descriptor in scene state)
     * Payload: ShowPayload with block class and data
     * <p>
     * Data flow:
     * <ol>
     *   <li>View emits ACTION("edit", {id: "123"})</li>
     *   <li>Block translates the intent to SHOW(EditBlock.class, {id: "123"})</li>
     *   <li>SceneEventHandler receives SHOW and selects a descriptor on-demand</li>
     * </ol>
     */
    public static final EventKey.SimpleKey<ShowPayload> SHOW =
            new EventKey.SimpleKey<>("show", ShowPayload.class);

    /**
     * Internal layer show event emitted after placement resolution.
     * <p>
     * Application blocks should continue to publish {@link #SHOW}. The scene
     * layer decides whether the target should replace inline content or open a
     * layer, then forwards layer-bound targets with this event.
     */
    public static final EventKey.SimpleKey<ShowPayload> SHOW_LAYER =
            new EventKey.SimpleKey<>("show.layer", ShowPayload.class);

    public static final EventKey.SimpleKey<Class> SET_PRIMARY =
            new EventKey.SimpleKey<>("setPrimary", Class.class);


    /**
     * Hide a block (destroy instance).
     * Emitted by: Views (close button), Blocks (after save/delete)
     * Handled by: LayerComponent, which removes the descriptor and unmounts
     * its DirectBlockHost.
     * Payload: Block class to hide (always explicit about what to close)
     * <p>
     * Unlike CLOSE_OVERLAY which is generic, HIDE always specifies which
     * block to close. This supports multiple overlays being shown.
     */
    @SuppressWarnings("unchecked")
    public static final EventKey.SimpleKey<Class<? extends Block<?, ?>>> HIDE =
            new EventKey.SimpleKey<>("hide",
                    (Class<Class<? extends Block<?, ?>>>) (Class<?>) Class.class);


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
     * Emitted by blocks when {@link ContextKeys#SCENE} carries an effective
     * URL. Handled by {@link SceneEventHandler}, which updates that effective
     * URL and pushes browser history while preserving companion runtimes.
     */
    public static final EventKey.SimpleKey<SceneQueryUpdate> SCENE_QUERY_UPDATED =
            new EventKey.SimpleKey<>("scene.query.updated", SceneQueryUpdate.class);

    /**
     * Reports the title produced by a mounted block runtime. Scene state uses
     * this only for the matching primary descriptor, keeping title resolution
     * inside the component tree without letting companions overwrite it.
     */
    public static final EventKey.SimpleKey<SceneTitleUpdate> SCENE_TITLE_UPDATED =
            new EventKey.SimpleKey<>("scene.title.updated", SceneTitleUpdate.class);

    /**
     * Announces that the component tree mounted the currently routed block.
     * Consumers that need a live block, such as an agent sidebar, keep their
     * own local reference rather than reading one from Scene state.
     */
    public static final EventKey.SimpleKey<MountedPrimaryBlock> PRIMARY_BLOCK_MOUNTED =
            new EventKey.SimpleKey<>("scene.primary.block.mounted", MountedPrimaryBlock.class);

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

    public record MountedPrimaryBlock(long descriptorId, Block<?, ?> block) {
        public MountedPrimaryBlock {
            if (descriptorId < 1) {
                throw new IllegalArgumentException("descriptorId must be positive");
            }
            Objects.requireNonNull(block, "block");
        }
    }


    /**
     * Action succeeded (data event).
     * Emitted by: form block components after successful operations
     * Payload: ActionResult containing block class
     * <p>
     * This is a data event — blocks decide their own post-action behavior.
     * The framework does not impose auto-close or auto-navigate heuristics.
     * Blocks that want to close after success should publish HIDE themselves.
     */
    public static final EventKey.SimpleKey<ActionResult> ACTION_SUCCESS =
            new EventKey.SimpleKey<>("action.success", ActionResult.class);

    /**
     * Action result payload.
     *
     * @param blockClass The class of the block that performed the action
     */
    public record ActionResult(
        Class<? extends Block<?, ?>> blockClass
    ) {}

}
