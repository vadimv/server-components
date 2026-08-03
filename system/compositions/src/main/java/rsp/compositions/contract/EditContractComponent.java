package rsp.compositions.contract;

import rsp.component.ComponentView;
import rsp.component.Lookup;
import rsp.component.StateUpdater;
import rsp.compositions.ui.EditView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Base component contract for editing and deleting an existing entity. */
public abstract class EditContractComponent<T> extends FormContractComponent<T> {

    protected EditContractComponent(ComponentView<EditView.EditViewState, EditView.EditIntent> view) {
        super(view);
    }

    @Override
    protected final boolean isCreateMode() {
        return false;
    }

    protected abstract String resolveIdFromPath(Lookup lookup);

    protected abstract T item(String id);

    protected abstract boolean delete(String id);

    protected final String resolveId() {
        return resolveId(lookup());
    }

    @Override
    protected final T item(Lookup lookup) {
        return item(resolveId(lookup));
    }

    @Override
    protected void onContractMounted(EditView.EditViewState state,
                                     StateUpdater<EditView.EditViewState> stateUpdate) {
        super.onContractMounted(state, stateUpdate);
        subscribe(EditContractEvents.DELETE_REQUESTED, () -> deleteCurrent());
    }

    @Override
    protected void onIntent(EditView.EditIntent intent,
                            EditView.EditViewState state,
                            StateUpdater<EditView.EditViewState> stateUpdater) {
        super.onIntent(intent, state, stateUpdater);
        if (intent == EditView.DeleteConfirmed.INSTANCE) {
            deleteCurrent();
        }
    }

    @Override
    public List<ContractAction> agentActions() {
        List<ContractAction> actions = new ArrayList<>(super.agentActions());
        actions.add(new ContractAction("delete", EditContractEvents.DELETE_REQUESTED,
                "Delete the current entity", DispatchEffect.SCENE_CHANGE));
        return List.copyOf(actions);
    }

    private String resolveId(Lookup lookup) {
        Map<String, Object> showData = lookup.get(ContextKeys.SHOW_DATA);
        if (showData != null && showData.get("id") != null) {
            return String.valueOf(showData.get("id"));
        }
        return resolveIdFromPath(lookup);
    }

    private void deleteCurrent() {
        if (delete(resolveId())) {
            publishSuccess();
        }
    }
}
