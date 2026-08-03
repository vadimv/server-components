package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.component.definitions.ContextStateComponent;
import rsp.compositions.schema.DataSchema;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static rsp.compositions.contract.ActionBindings.ShowPayload;
import static rsp.compositions.contract.EventKeys.ACTION_SUCCESS;
import static rsp.compositions.contract.EventKeys.SCENE_QUERY_UPDATED;
import static rsp.compositions.contract.EventKeys.SHOW;
import static rsp.compositions.contract.ListView.BulkDeleteConfirmed;
import static rsp.compositions.contract.ListView.CreateRequested;
import static rsp.compositions.contract.ListView.EditRequested;
import static rsp.compositions.contract.ListView.ListIntent;
import static rsp.compositions.contract.ListView.ListViewState;
import static rsp.compositions.contract.ListView.PageRequested;
import static rsp.compositions.contract.ListView.SelectionChanged;
import static rsp.compositions.contract.ListView.SortRequested;

/**
 * Intent-driven base for a paginated list contract.
 *
 * <p>This component owns list cache updates, agent commands, navigation, and
 * deletion effects. Its supplied {@link ComponentView} receives only state and
 * an intent dispatcher.</p>
 *
 * @param <T> domain item type
 */
public abstract class ListContractComponent<T>
        extends ContractNodeComponent<ListViewState, ListIntent> {

    public static final String CONFIG_DEFAULT_PAGE_SIZE = "list.defaultPageSize";
    private static final int DEFAULT_PAGE_SIZE_FALLBACK = 10;
    private static final String SORT_QUERY_PARAM = "sort";

    private final ComponentView<ListViewState, ListIntent> view;

    protected ListContractComponent(ComponentView<ListViewState, ListIntent> view) {
        this.view = java.util.Objects.requireNonNull(view, "view");
    }

    protected abstract QueryParam<Integer> pageQueryParam();

    protected abstract String sort(rsp.component.Lookup lookup);

    protected abstract List<T> items(int page, int pageSize, String sort);

    protected DataSchema customizeSchema(DataSchema schema) {
        return schema;
    }

    protected abstract Class<? extends Contract> createElementContract();

    protected abstract Class<? extends Contract> editElementContract();

    @Override
    public final ComponentStateSupplier<ListViewState> initStateSupplier() {
        return (_, context) -> initialState(context);
    }

    @Override
    public final ComponentView<ListViewState, ListIntent> componentView() {
        return view;
    }

    @Override
    protected void onContractMounted(ListViewState state, StateUpdater<ListViewState> stateUpdate) {
        // List components are reusable across query-only URL changes. Keep the
        // component-owned cache aligned when browser history changes externally.
        watch(ContextKeys.URL_QUERY.with(pageQueryParam().name),
                (_, _) -> refreshFromContext(stateUpdate));
        watch(ContextKeys.URL_QUERY.with(SORT_QUERY_PARAM),
                (_, _) -> refreshFromContext(stateUpdate));

        subscribe(ListContractEvents.CREATE_ELEMENT_REQUESTED,
                () -> lookup().publish(SHOW, new ShowPayload(createElementContract(), Map.of())));

        subscribe(ListContractEvents.EDIT_ELEMENT_REQUESTED,
                (_, rowId) -> lookup().publish(SHOW, new ShowPayload(editElementContract(), Map.of("id", rowId))));

        subscribe(ListContractEvents.BULK_DELETE_REQUESTED,
                (_, selectedIds) -> stateUpdate.applyStateTransformation(current -> {
                    handleBulkDelete(selectedIds);
                    ListViewState cleared = current.clearSelection();
                    publishSelection(cleared.selectedIds());
                    return cleared;
                }));

        subscribe(ListContractEvents.PAGE_CHANGE_REQUESTED,
                (_, page) -> stateUpdate.applyStateTransformation(current -> {
                    ListViewState updated = reload(current, page, current.sort(), Set.of());
                    publishSelection(updated.selectedIds());
                    publishPageChange(page);
                    return updated;
                }));

        subscribe(ListContractEvents.SELECT_ALL_REQUESTED, () -> stateUpdate.applyStateTransformation(current -> {
            ListViewState selected = current.selectAll();
            publishSelection(selected.selectedIds());
            return selected;
        }));

        subscribe(ListContractEvents.EDIT_SELECTED_REQUESTED, () -> stateUpdate.applyStateTransformation(current -> {
            if (!current.selectedIds().isEmpty()) {
                lookup().publish(SHOW, new ShowPayload(editElementContract(),
                        Map.of("id", current.selectedIds().iterator().next())));
            }
            return current;
        }));

        subscribe(ListContractEvents.DELETE_SELECTED_REQUESTED, () -> stateUpdate.applyStateTransformation(current -> {
            if (!current.selectedIds().isEmpty()) {
                handleBulkDelete(current.selectedIds());
            }
            return current;
        }));
    }

    @Override
    protected void onIntent(ListIntent intent, ListViewState state, StateUpdater<ListViewState> stateUpdater) {
        if (intent instanceof SelectionChanged selection) {
            ListViewState updated = withSelection(state, selection.selectedIds());
            stateUpdater.setState(updated);
            publishSelection(updated.selectedIds());
        } else if (intent instanceof BulkDeleteConfirmed bulkDelete) {
            handleBulkDelete(bulkDelete.selectedIds());
            ListViewState cleared = state.clearSelection();
            stateUpdater.setState(cleared);
            publishSelection(cleared.selectedIds());
        } else if (intent instanceof PageRequested pageRequested) {
            ListViewState updated = reload(state, pageRequested.page(), state.sort(), Set.of());
            stateUpdater.setState(updated);
            publishSelection(updated.selectedIds());
            publishPageChange(pageRequested.page());
        } else if (intent instanceof SortRequested sortRequested) {
            stateUpdater.setState(reload(state, state.page(), sortRequested.sort(), state.selectedIds()));
            publishQueryChange(SORT_QUERY_PARAM, sortRequested.sort());
        } else if (intent == CreateRequested.INSTANCE) {
            lookup().publish(SHOW, new ShowPayload(createElementContract(), Map.of()));
        } else if (intent instanceof EditRequested editRequested) {
            lookup().publish(SHOW, new ShowPayload(editElementContract(), Map.of("id", editRequested.rowId())));
        }
    }

    @Override
    public List<ContractAction> agentActions() {
        return List.of(
                new ContractAction("create", ListContractEvents.CREATE_ELEMENT_REQUESTED,
                        "Open create form for a new item", DispatchEffect.SCENE_CHANGE),
                new ContractAction("edit", ListContractEvents.EDIT_ELEMENT_REQUESTED,
                        "Open edit form for an item", new PayloadSchema.StringValue("row ID"),
                        DispatchEffect.SCENE_CHANGE),
                new ContractAction("edit_selected", ListContractEvents.EDIT_SELECTED_REQUESTED,
                        "Open edit form for the first selected row", DispatchEffect.SCENE_CHANGE),
                new ContractAction("delete", ListContractEvents.BULK_DELETE_REQUESTED,
                        "Delete items by their IDs", new PayloadSchema.StringSet("row IDs to delete")),
                new ContractAction("delete_selected", ListContractEvents.DELETE_SELECTED_REQUESTED,
                        "Delete all currently selected rows"),
                new ContractAction("page", ListContractEvents.PAGE_CHANGE_REQUESTED,
                        "Navigate to a page number", new PayloadSchema.IntegerValue("page number (1-based)")),
                new ContractAction("select_all", ListContractEvents.SELECT_ALL_REQUESTED,
                        "Select all rows on the current page"));
    }

    @Override
    public ContractMetadata contractMetadata() {
        int page = pageQueryParam().resolve(lookup());
        String sort = sort(lookup());
        List<T> items = items(page, lookup().getInt(CONFIG_DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZE_FALLBACK), sort);
        DataSchema schema = schema(items);
        return new ContractMetadata(title(), "Paginated data list", schema,
                Map.of("page", page, "pageSize", lookup().getInt(CONFIG_DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZE_FALLBACK),
                        "sort", sort, "items", schema.toMapList(items)));
    }

    protected int bulkDelete(Set<String> ids) {
        throw new UnsupportedOperationException("Bulk delete not implemented. Override bulkDelete() in your contract.");
    }

    protected void onBulkDeleteFailure(Set<String> failedIds) {
    }

    private ListViewState initialState(ComponentContext context) {
        rsp.component.Lookup lookup = LookupFactory.create(context);
        int page = pageQueryParam().resolve(lookup);
        String sort = sort(lookup);
        int pageSize = lookup.getInt(CONFIG_DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZE_FALLBACK);
        List<T> items = items(page, pageSize, sort);
        DataSchema schema = schema(items);
        List<Map<String, Object>> rows = items.isEmpty() ? List.of() : schema.toMapList(items);
        return new ListViewState(rows, schema, page, sort, modulePath(context), Set.of(), title(), editTarget(context));
    }

    private DataSchema schema(List<T> items) {
        if (items.isEmpty()) {
            return customizeSchema(new DataSchema(List.of()));
        }
        return customizeSchema(DataSchema.fromFirstItem(items.getFirst()));
    }

    private void publishSelection(Set<String> selectedIds) {
        lookup().publish(ListContractEvents.SELECTION_CHANGED, new ListContractEvents.SelectedItems(selectedIds));
    }

    private void publishPageChange(int page) {
        publishQueryChange(pageQueryParam().name, String.valueOf(page));
    }

    private void publishQueryChange(String name, String value) {
        Scene scene = lookup().get(ContextKeys.SCENE);
        if (scene != null && scene.effectiveUrl() != null) {
            lookup().publish(SCENE_QUERY_UPDATED, new EventKeys.SceneQueryUpdate(name, value));
            return;
        }
        lookup().publish(EventKeys.STATE_UPDATED.with(name),
                new ContextStateComponent.ContextValue.StringValue(value));
    }

    private void refreshFromContext(StateUpdater<ListViewState> stateUpdater) {
        stateUpdater.applyStateTransformation(current -> {
            int page = pageQueryParam().resolve(lookup());
            String sort = sort(lookup());
            Set<String> selectedIds = page == current.page() ? current.selectedIds() : Set.of();
            return reload(current, page, sort, selectedIds);
        });
    }

    private ListViewState reload(ListViewState current,
                                 int page,
                                 String sort,
                                 Set<String> selectedIds) {
        int pageSize = lookup().getInt(CONFIG_DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZE_FALLBACK);
        List<T> pageItems = items(page, pageSize, sort);
        DataSchema schema = schema(pageItems);
        List<Map<String, Object>> rows = pageItems.isEmpty() ? List.of() : schema.toMapList(pageItems);
        return new ListViewState(rows, schema, page, sort, current.modulePath(), selectedIds,
                current.title(), current.editTarget());
    }

    private void handleBulkDelete(Set<String> selectedIds) {
        int deletedCount = bulkDelete(selectedIds);
        if (deletedCount > 0) {
            lookup().publish(ACTION_SUCCESS, new EventKeys.ActionResult(getClass()));
        } else {
            onBulkDeleteFailure(selectedIds);
        }
    }

    private static ListViewState withSelection(ListViewState state, Set<String> selectedIds) {
        return new ListViewState(state.rows(), state.schema(), state.page(), state.sort(), state.modulePath(),
                selectedIds, state.title(), state.editTarget());
    }

    private static String modulePath(ComponentContext context) {
        String routePath = context.get(ContextKeys.ROUTE_PATH);
        if (routePath == null) {
            return "/";
        }
        int queryStart = routePath.indexOf('?');
        return queryStart == -1 ? routePath : routePath.substring(0, queryStart);
    }

    private static ListView.EditTarget editTarget(ComponentContext context) {
        Boolean hasRoute = context.get(ContextKeys.EDIT_HAS_ROUTE);
        Boolean opensAsOverlay = context.get(ContextKeys.EDIT_OPENS_AS_OVERLAY);
        String routePattern = context.get(ContextKeys.EDIT_ROUTE_PATTERN);
        return new ListView.EditTarget(Boolean.TRUE.equals(hasRoute), Boolean.TRUE.equals(opensAsOverlay), routePattern);
    }
}
