package rsp.compositions.block;

import rsp.component.*;
import rsp.component.definitions.AddressBarSyncComponent;
import rsp.compositions.composition.Composition;
import rsp.compositions.layout.PlacementDecision;
import rsp.compositions.routing.Router;
import rsp.server.http.RelativeUrl;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static rsp.compositions.block.ActionBindings.ShowPayload;
import static rsp.compositions.block.EventKeys.*;

/**
 * Registers and handles Scene lifecycle events for the base layer (routed + companions).
 * <p>
 * Events handled:
 * <ul>
 *   <li>SHOW - Resolve placement for on-demand blocks</li>
 *   <li>SET_PRIMARY - Replace the routed block</li>
 *   <li>ACTION_SUCCESS - Refresh routed block in place (data may have changed)</li>
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

        subscriber.addEventHandler(SET_PRIMARY, (eventName, blockKey) -> {
            handleSetPrimary(state, blockKey, stateUpdate, commandsEnqueue);
        }, false);

        // ACTION_SUCCESS handler: refresh routed block in place
        subscriber.addEventHandler(ACTION_SUCCESS, (eventName, result) -> {
            handleActionSuccess(state, result, commandsEnqueue, stateUpdate);
        }, false);

        subscriber.addEventHandler(SCENE_QUERY_UPDATED, (eventName, update) -> {
            handleSceneQueryUpdated(state, update, commandsEnqueue, stateUpdate);
        }, false);

        subscriber.addEventHandler(EventKeys.SCENE_QUERY_UPDATED_BATCH, (eventName, update) -> {
            handleSceneQueryUpdates(state, update, commandsEnqueue, stateUpdate);
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
        Object blockKey = payload.blockKey();
        if (!state.blocks().hasBinding(blockKey)) {
            return;
        }
        PlacementDecision decision = state.composition().layout()
                .resolvePlacement(state.blocks().target(blockKey), state);

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
        Object blockKey = payload.blockKey();

        if (state.isRouted(blockKey)) {
            return;
        }

        // Capture the current routed block as a return target before destroying it,
        // so a later ACTION_SUCCESS from the inline form can restore the previous view
        // (e.g., Save/Cancel on an inline edit form returns to the list).
        final SceneNavigator navigator = new SceneNavigator(savedContext, commandsEnqueue);
        final Scene.InlineReturnTarget returnTarget = navigator.captureInlineReturnTarget(state);

        BlockDescriptor descriptor = describeBlock(state, blockKey, payload.data());
        if (descriptor == null) {
            return;
        }

        // Update the URL bar to reflect the now-routed inline block (e.g. /comments/3).
        // The Router's pattern is the source of truth for URL shape; we substitute path
        // parameters from the SHOW payload data and preserve the current query state.
        RelativeUrl targetUrl = navigator.pushInlineUrl(state, blockKey, payload.data());

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

    private void handleSceneQueryUpdates(Scene state,
                                         EventKeys.SceneQueryUpdates updates,
                                         CommandsEnqueue commandsEnqueue,
                                         StateUpdater<Scene> stateUpdate) {
        RelativeUrl updatedUrl = new SceneNavigator(savedContext, commandsEnqueue)
                .pushSceneQueryUpdates(state.effectiveUrl(), updates);
        if (updatedUrl != null) {
            stateUpdate.applyStateTransformation(s -> s.withEffectiveUrl(updatedUrl));
        }
    }

    private void handleSceneTitleUpdated(Scene state,
                                         EventKeys.SceneTitleUpdate update,
                                         StateUpdater<Scene> stateUpdate) {
        BlockDescriptor routed = state.routedDescriptor();
        boolean isPrimary = routed != null && routed.instanceId() == update.descriptorId();
        BlockDescriptor autoOpen = state.autoOpen() == null
                ? null
                : state.preActivatedDescriptor(state.autoOpen().blockKey());
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

        Object targetKey = match.get().blockKey();
        if (state.isRouted(targetKey)) {
            stateUpdate.applyStateTransformation(s -> s.withEffectiveUrl(targetUrl));
            return;
        }

        BlockDescriptor descriptor = describeBlockForUrl(state, targetKey);
        if (descriptor == null) {
            return;
        }

        stateUpdate.applyStateTransformation(s ->
                s.withRoutedDescriptor(descriptor)
                        .clearInlineReturnTarget()
                        .withEffectiveUrl(targetUrl));
    }

    /**
     * Handle SET_PRIMARY event: replace the routed block.
     */
    private void handleSetPrimary(Scene state, Object blockKey,
                                  StateUpdater<Scene> stateUpdate,
                                  CommandsEnqueue commandsEnqueue) {
        // Check if already the routed block
        if (state.isRouted(blockKey)) {
            return;
        }

        BlockDescriptor descriptor = describeBlock(state, blockKey, Map.of());
        if (descriptor == null) {
            return;
        }

        // Update URL to reflect the new routed block's route.
        // SET_PRIMARY is a fresh primary-block selection, so the navigator
        // uses an empty query and fragment for the target URL.
        RelativeUrl targetUrl = new SceneNavigator(savedContext, commandsEnqueue)
                .pushPrimaryUrl(state, blockKey);

        // SET_PRIMARY is a fresh navigation — clear any pending inline return target
        // so a subsequent ACTION_SUCCESS does not bounce the user back to a stale view.
        final RelativeUrl effectiveUrl = targetUrl;
        stateUpdate.applyStateTransformation(s ->
                effectiveUrl != null
                        ? s.withRoutedDescriptor(descriptor).clearInlineReturnTarget().withEffectiveUrl(effectiveUrl)
                        : s.withRoutedDescriptor(descriptor).clearInlineReturnTarget());
    }

    /**
     * Resolve a fresh descriptor for a block that will mount in the tree.
     */
    private BlockDescriptor describeBlock(Scene state, Object blockKey) {
        return describeBlock(state, blockKey, Map.of());
    }

    /**
     * Select a fresh component-owned block descriptor.
     */
    private BlockDescriptor describeBlock(Scene state, Object blockKey,
                                                  Map<String, Object> showData) {
        Composition composition = state.composition();
        if (composition == null || !composition.blocks().hasBinding(blockKey)) {
            return null;
        }
        return BlockDescriptor.forTarget(composition.blocks().target(blockKey), showData);
    }

    private BlockDescriptor describeBlockForUrl(Scene state,
                                                 Object blockKey) {
        if (state.composition() == null
                || !state.composition().blocks().hasBinding(blockKey)) {
            return null;
        }
        return BlockDescriptor.forTarget(state.composition().blocks().target(blockKey), Map.of());
    }

    /**
     * Handle ACTION_SUCCESS: refresh the routed block, or restore the previous
     * routed block if the current routed runtime is an inline replacement.
     * <p>
     * Restore path: when an inline form (e.g., create/edit replacing a list inline)
     * completes its action, navigate back to the captured previous routed block,
     * preserving the query state and fragment that were active at SHOW time.
     * <p>
     * Refresh path (existing behaviour): any successful action — from the routed
     * block or from a layer — may have modified data visible in the routed view.
     * Re-instantiate the routed block, preserving its query state.
     */
    private void handleActionSuccess(Scene state,
                                     ActionResult result,
                                     CommandsEnqueue commandsEnqueue,
                                     StateUpdater<Scene> stateUpdate) {
        Object blockKey = result.blockKey();
        if (state.routedDescriptor() == null) {
            return;
        }

        Scene.InlineReturnTarget returnTarget = state.inlineReturnTarget();
        if (returnTarget != null
                && state.routedDescriptor().blockKey().equals(blockKey)) {
            restoreInlineReturn(state, returnTarget, commandsEnqueue, stateUpdate);
            return;
        }

        // In-place refresh — same block class, preserve query state.
        Object routedKey = state.routedDescriptor().blockKey();
        BlockDescriptor refreshed = describeBlock(state, routedKey);
        if (refreshed != null) {
            stateUpdate.applyStateTransformation(s -> s.withRoutedDescriptor(refreshed));
        }
    }

    private void restoreInlineReturn(Scene state,
                                     Scene.InlineReturnTarget target,
                                     CommandsEnqueue commandsEnqueue,
                                     StateUpdater<Scene> stateUpdate) {
        BlockDescriptor restored = describeBlock(state, target.blockKey());
        if (restored == null) {
            return;
        }

        RelativeUrl url = new SceneNavigator(savedContext, commandsEnqueue)
                .pushReturnUrl(target);

        stateUpdate.applyStateTransformation(s ->
                s.withRoutedDescriptor(restored).clearInlineReturnTarget().withEffectiveUrl(url));
    }
}
