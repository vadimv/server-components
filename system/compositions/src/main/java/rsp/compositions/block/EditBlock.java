package rsp.compositions.block;

import rsp.component.ComponentView;
import rsp.component.Lookup;
import rsp.component.StateUpdater;
import rsp.compositions.ui.EditView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Base component block for editing and deleting an existing entity. */
public abstract class EditBlock<T> extends FormBlock<T> {

    protected EditBlock(ComponentView<EditView.EditViewState, EditView.EditIntent> view) {
        super(view);
    }

    @Override
    protected final boolean isCreateMode() {
        return false;
    }

    protected abstract String resolveIdFromPath(Lookup lookup);

    protected abstract T item(String id);

    protected abstract boolean delete(String id);

    /** Compatibility adapter for existing boolean delete implementations. */
    protected FormMutationResult deleteResult(String id) {
        return delete(id)
                ? FormMutationResult.saved(id, "Deleted successfully.")
                : FormMutationResult.notFound("The item no longer exists.");
    }

    @Override
    protected boolean canDelete() {
        return true;
    }

    protected final String resolveId() {
        return resolveId(lookup());
    }

    @Override
    protected final T item(Lookup lookup) {
        return item(resolveId(lookup));
    }

    @Override
    protected void onBlockMounted(EditView.EditViewState state,
                                     StateUpdater<EditView.EditViewState> stateUpdate) {
        super.onBlockMounted(state, stateUpdate);
        subscribe(EditBlockEvents.DELETE_REQUESTED,
                () -> deleteCurrent(stateUpdate));
    }

    @Override
    protected void onIntent(EditView.EditIntent intent,
                            EditView.EditViewState state,
                            StateUpdater<EditView.EditViewState> stateUpdater) {
        super.onIntent(intent, state, stateUpdater);
        if (intent == EditView.DeleteConfirmed.INSTANCE) {
            deleteCurrent(stateUpdater);
        }
    }

    @Override
    public List<BlockAction> agentActions() {
        List<BlockAction> actions = new ArrayList<>(super.agentActions());
        if (currentCapabilities().canDelete()) {
            actions.add(new BlockAction("delete", EditBlockEvents.DELETE_REQUESTED,
                    "Delete the current entity", DispatchEffect.SCENE_CHANGE));
        }
        return List.copyOf(actions);
    }

    private String resolveId(Lookup lookup) {
        Map<String, Object> showData = lookup.get(ContextKeys.SHOW_DATA);
        if (showData != null && showData.get("id") != null) {
            return String.valueOf(showData.get("id"));
        }
        return resolveIdFromPath(lookup);
    }

    private void deleteCurrent(StateUpdater<EditView.EditViewState> stateUpdater) {
        stateUpdater.applyStateTransformation(current -> {
            if (!beginMutation()) return current;
            try {
                if (!current.capabilities().canDelete() || current.isBusy()
                        || !current.status().canRenderFields()) {
                    return current;
                }
                EditView.EditViewState deleting = copyState(current, current.fieldValues(), current.isDirty(),
                        FormStatus.DELETING, current.validationErrors(), "Deleting…", false);
                String id = resolveId();
                if (id == null || id.isBlank()) {
                    return copyState(deleting, deleting.fieldValues(), deleting.isDirty(), FormStatus.NOT_FOUND,
                            Map.of(), "The item identifier is missing.", true);
                }
                try {
                    FormMutationResult result = java.util.Objects.requireNonNull(deleteResult(id), "deleteResult");
                    if (result.succeeded()) {
                        publishSuccess();
                        return deleting;
                    }
                    return stateForResult(deleting, result, "The item could not be deleted.");
                } catch (RuntimeException exception) {
                    return copyState(deleting, deleting.fieldValues(), deleting.isDirty(), FormStatus.FAILED,
                            Map.of(), "The item could not be deleted.", true);
                }
            } finally {
                endMutation();
            }
        });
    }
}
