package rsp.compositions.block;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.Lookup;
import rsp.component.StateUpdater;
import rsp.component.definitions.ContextStateComponent;
import rsp.compositions.schema.ColumnConfig;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldDef;
import rsp.compositions.composition.Composition;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static rsp.compositions.block.ActionBindings.ShowPayload;
import static rsp.compositions.block.EventKeys.SCENE_QUERY_UPDATED_BATCH;
import static rsp.compositions.block.EventKeys.SHOW;
import static rsp.compositions.block.ListView.BulkDeleteConfirmed;
import static rsp.compositions.block.ListView.CreateRequested;
import static rsp.compositions.block.ListView.DeleteConfirmed;
import static rsp.compositions.block.ListView.DismissMessage;
import static rsp.compositions.block.ListView.EditRequested;
import static rsp.compositions.block.ListView.ListIntent;
import static rsp.compositions.block.ListView.ListViewState;
import static rsp.compositions.block.ListView.PageRequested;
import static rsp.compositions.block.ListView.PageSizeRequested;
import static rsp.compositions.block.ListView.QueryRequested;
import static rsp.compositions.block.ListView.SelectionChanged;
import static rsp.compositions.block.ListView.SortRequested;

/**
 * Intent-driven base for a schema-backed, queryable data list.
 *
 * <p>The schema is independent of loaded rows, so empty result pages retain
 * their headers and behavior. Loaders receive one validated {@link ListQuery}
 * and return both rows and a total through {@link ListPage}.</p>
 *
 * @param <T> domain item type
 */
public abstract class ListBlock<T> extends Block<ListViewState, ListIntent> {
    public static final String CONFIG_DEFAULT_PAGE_SIZE = "list.defaultPageSize";
    public static final String QUERY_PAGE_SIZE = "size";
    public static final String QUERY_SORT_FIELD = "sort";
    public static final String QUERY_SORT_DIRECTION = "dir";
    public static final String QUERY_SEARCH = "q";
    public static final String QUERY_FILTER_PREFIX = "filter.";

    private static final int DEFAULT_PAGE_SIZE_FALLBACK = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final ComponentView<ListViewState, ListIntent> view;
    private final AtomicBoolean deleteInFlight = new AtomicBoolean();

    protected ListBlock(ComponentView<ListViewState, ListIntent> view) {
        this.view = java.util.Objects.requireNonNull(view, "view");
    }

    /** Query parameter used for this list's one-based page number. */
    protected abstract QueryParam<Integer> pageQueryParam();

    /** Stable schema used even when a query returns no rows. */
    protected abstract DataSchema listSchema();

    /** Load one page and the exact total number of matching rows. */
    protected abstract ListPage<T> items(ListQuery query);

    /** Block opened by the create action. */
    protected abstract Class<? extends Block<?, ?>> createElementBlock();

    /** Block opened by an edit action. */
    protected abstract Class<? extends Block<?, ?>> editElementBlock();

    /** Inverse relationship links appended as columns by the default grid. */
    protected List<RelatedListLinkSpec> relatedListLinks() {
        return List.of();
    }

    /** Initial sort; by default, the first sortable column in the schema. */
    protected SortSpec defaultSort() {
        DataSchema schema = listSchema();
        return schema.listColumns().stream()
                .filter(field -> schema.columnConfig(field.name()).sortable())
                .findFirst()
                .or(() -> schema.listColumns().stream().findFirst())
                .map(field -> new SortSpec(field.name(), SortDirection.ASC))
                .orElse(null);
    }

    /** Schema field used as the stable row identity. */
    protected String rowKey() {
        return "id";
    }

    /** Whether the view and intent boundary permit creation. */
    protected boolean canCreate() {
        return true;
    }

    /** Whether the view and intent boundary permit editing. */
    protected boolean canEdit() {
        return true;
    }

    /** Whether the view and intent boundary permit deletion. */
    protected boolean canDelete() {
        return true;
    }

    @Override
    public final ComponentStateSupplier<ListViewState> initStateSupplier() {
        return (_, context) -> initialState(context);
    }

    @Override
    public final ComponentView<ListViewState, ListIntent> componentView() {
        return view;
    }

    @Override
    protected void onBlockMounted(ListViewState state, StateUpdater<ListViewState> stateUpdate) {
        watch(ContextKeys.URL_QUERY.with(pageQueryParam().name), (_, _) -> refreshFromContext(stateUpdate));
        watch(ContextKeys.URL_QUERY.with(QUERY_PAGE_SIZE), (_, _) -> refreshFromContext(stateUpdate));
        watch(ContextKeys.URL_QUERY.with(QUERY_SORT_FIELD), (_, _) -> refreshFromContext(stateUpdate));
        watch(ContextKeys.URL_QUERY.with(QUERY_SORT_DIRECTION), (_, _) -> refreshFromContext(stateUpdate));
        watch(ContextKeys.URL_QUERY.with(QUERY_SEARCH), (_, _) -> refreshFromContext(stateUpdate));
        for (FieldDef field : filterableColumns(state.schema())) {
            watch(ContextKeys.URL_QUERY.with(QUERY_FILTER_PREFIX + field.name()),
                    (_, _) -> refreshFromContext(stateUpdate));
        }

        subscribe(ListBlockEvents.CREATE_ELEMENT_REQUESTED, () -> {
            if (canCreate() && !deleteInFlight.get()) {
                lookup().publish(SHOW, new ShowPayload(createElementBlock(), Map.of()));
            }
        });

        subscribe(ListBlockEvents.EDIT_ELEMENT_REQUESTED, (_, rowId) -> {
            if (canEdit() && !deleteInFlight.get()) {
                lookup().publish(SHOW, new ShowPayload(editElementBlock(), Map.of("id", rowId)));
            }
        });

        subscribe(ListBlockEvents.BULK_DELETE_REQUESTED,
                (_, selectedIds) -> stateUpdate.applyStateTransformation(current -> {
                    requestDelete(current, selectedIds, stateUpdate);
                    return current;
                }));

        subscribe(ListBlockEvents.PAGE_CHANGE_REQUESTED, (_, page) -> {
            if (!deleteInFlight.get()) {
                stateUpdate.applyStateTransformation(current ->
                        current.isBusy() ? current : changePage(current, page, true));
            }
        });

        subscribe(ListBlockEvents.SELECT_ALL_REQUESTED, () -> stateUpdate.applyStateTransformation(current -> {
            if (current.isBusy() || deleteInFlight.get()) {
                return current;
            }
            ListViewState selected = current.selectAll();
            publishSelection(selected.selectedIds());
            return selected;
        }));

        subscribe(ListBlockEvents.EDIT_SELECTED_REQUESTED, () -> stateUpdate.applyStateTransformation(current -> {
            if (canEdit() && !current.isBusy() && !deleteInFlight.get() && !current.selectedIds().isEmpty()) {
                lookup().publish(SHOW, new ShowPayload(editElementBlock(),
                        Map.of("id", current.selectedIds().iterator().next())));
            }
            return current;
        }));

        subscribe(ListBlockEvents.DELETE_SELECTED_REQUESTED,
                () -> stateUpdate.applyStateTransformation(current -> {
                    requestDelete(current, current.selectedIds(), stateUpdate);
                    return current;
                }));
    }

    @Override
    protected void onIntent(ListIntent intent, ListViewState state, StateUpdater<ListViewState> stateUpdater) {
        if (state.isBusy()) {
            return;
        }
        if (intent instanceof SelectionChanged selection) {
            ListViewState updated = withSelection(state, selection.selectedIds());
            stateUpdater.setState(updated);
            publishSelection(updated.selectedIds());
        } else if (intent instanceof BulkDeleteConfirmed bulkDelete) {
            requestDelete(state, bulkDelete.selectedIds(), stateUpdater);
        } else if (intent instanceof DeleteConfirmed delete) {
            requestDelete(state, Set.of(delete.rowId()), stateUpdater);
        } else if (intent instanceof PageRequested pageRequested) {
            stateUpdater.setState(changePage(state, pageRequested.page(), true));
        } else if (intent instanceof PageSizeRequested pageSizeRequested) {
            int pageSize = normalizePageSize(pageSizeRequested.pageSize(), state.pageSize());
            ListQuery query = state.query().withPageSize(pageSize);
            stateUpdater.setState(reload(state, query, Set.of(), "", false));
            publishSelection(Set.of());
            publishQueryChanges(Map.of(pageQueryParam().name, "", QUERY_PAGE_SIZE, String.valueOf(pageSize)));
        } else if (intent instanceof SortRequested sortRequested) {
            SortSpec requested = sortRequested.sort();
            if (ListView.isLegacySortField(requested.field())) {
                SortSpec current = state.query().sort() == null ? defaultSort() : state.query().sort();
                requested = current == null ? null : new SortSpec(current.field(), requested.direction());
            }
            SortSpec sort = normalizeSort(requested, state.schema());
            ListQuery query = state.query().withSort(sort);
            stateUpdater.setState(reload(state, query, Set.of(), "", false));
            publishSelection(Set.of());
            publishSortQuery(query);
        } else if (intent instanceof QueryRequested requested) {
            Map<String, String> filters = normalizeFilters(requested.filters(), state.schema());
            ListQuery query = state.query().withCriteria(requested.search(), filters);
            stateUpdater.setState(reload(state, query, Set.of(), "", false));
            publishSelection(Set.of());
            publishCriteriaQuery(state.schema(), query);
        } else if (intent == CreateRequested.INSTANCE) {
            if (canCreate()) {
                lookup().publish(SHOW, new ShowPayload(createElementBlock(), Map.of()));
            }
        } else if (intent instanceof EditRequested editRequested) {
            if (canEdit()) {
                lookup().publish(SHOW, new ShowPayload(editElementBlock(), Map.of("id", editRequested.rowId())));
            }
        } else if (intent == DismissMessage.INSTANCE) {
            stateUpdater.setState(state.withMessage("", false));
        }
    }

    @Override
    public List<BlockAction> agentActions() {
        List<BlockAction> actions = new java.util.ArrayList<>();
        if (canCreate()) {
            actions.add(new BlockAction("create", ListBlockEvents.CREATE_ELEMENT_REQUESTED,
                    "Open create form for a new item", DispatchEffect.SCENE_CHANGE));
        }
        if (canEdit()) {
            actions.add(new BlockAction("edit", ListBlockEvents.EDIT_ELEMENT_REQUESTED,
                    "Open edit form for an item", new PayloadSchema.StringValue("row ID"),
                    DispatchEffect.SCENE_CHANGE));
            actions.add(new BlockAction("edit_selected", ListBlockEvents.EDIT_SELECTED_REQUESTED,
                    "Open edit form for the first selected row", DispatchEffect.SCENE_CHANGE));
        }
        if (canDelete()) {
            actions.add(new BlockAction("delete", ListBlockEvents.BULK_DELETE_REQUESTED,
                    "Delete items by their IDs", new PayloadSchema.StringSet("row IDs to delete")));
            actions.add(new BlockAction("delete_selected", ListBlockEvents.DELETE_SELECTED_REQUESTED,
                    "Delete all currently selected rows"));
        }
        actions.add(new BlockAction("page", ListBlockEvents.PAGE_CHANGE_REQUESTED,
                "Navigate to a page number", new PayloadSchema.IntegerValue("page number (1-based)")));
        actions.add(new BlockAction("select_all", ListBlockEvents.SELECT_ALL_REQUESTED,
                "Select all rows on the current page"));
        return List.copyOf(actions);
    }

    @Override
    public BlockMetadata blockMetadata() {
        DataSchema schema = listSchema();
        ListQuery query = resolveQuery(lookup(), schema);
        ListPage<T> page = items(query);
        List<ListView.RelatedListColumn> relatedLists = resolveRelatedListColumns(lookup(), schema);
        return new BlockMetadata(title(), "Queryable data list", schema,
                Map.of("page", query.page(), "pageSize", query.pageSize(), "totalItems", page.totalItems(),
                        "sort", query.sort() == null ? "" : query.sort(), "search", query.search(),
                        "filters", query.filters(), "items", schema.toMapList(page.items()),
                        "relatedLists", relatedLists));
    }

    /**
     * Delete the requested IDs. The result must report every requested ID in
     * exactly one of its outcome sets.
     */
    protected DeleteResult bulkDelete(Set<String> ids) {
        throw new UnsupportedOperationException("Bulk delete not implemented. Override bulkDelete() in your block.");
    }

    protected void onBulkDeleteFailure(Set<String> failedIds) {
    }

    private ListViewState initialState(ComponentContext context) {
        Lookup initialLookup = LookupFactory.create(context);
        DataSchema schema = listSchema();
        validateSchema(schema);
        validateDefaultSort(schema, defaultSort());
        if (schema.field(rowKey()) == null) {
            throw new IllegalStateException("Row key is not present in list schema: " + rowKey());
        }
        ListQuery query = resolveQuery(initialLookup, schema);
        List<ListView.RelatedListColumn> relatedLists = resolveRelatedListColumns(initialLookup, schema);
        return load(null, schema, query, Set.of(), title(), modulePath(context), editTarget(context),
                relatedLists, "", false);
    }

    private ListViewState changePage(ListViewState state, int requestedPage, boolean publish) {
        int maxPage = Math.max(1, state.totalPages());
        int page = Math.max(1, Math.min(requestedPage, maxPage));
        ListQuery query = state.query().withPage(page);
        ListViewState updated = reload(state, query, Set.of(), "", false);
        publishSelection(updated.selectedIds());
        if (publish) {
            publishQueryChanges(Map.of(pageQueryParam().name, page == 1 ? "" : String.valueOf(page)));
        }
        return updated;
    }

    private ListViewState deleteAndReload(ListViewState state, Set<String> requestedIds) {
        if (!canDelete() || !state.capabilities().canDelete() || requestedIds == null || requestedIds.isEmpty()) {
            return state;
        }
        try {
            DeleteResult result = bulkDelete(Set.copyOf(requestedIds));
            validateDeleteResult(requestedIds, result);
            if (!result.failedIds().isEmpty()) {
                onBulkDeleteFailure(result.failedIds());
            }
            String message;
            boolean error;
            if (result.deletedIds().isEmpty()) {
                message = "No items were deleted.";
                error = true;
            } else if (result.failedIds().isEmpty()) {
                message = result.deletedIds().size() == 1
                        ? "1 item deleted."
                        : result.deletedIds().size() + " items deleted.";
                error = false;
            } else {
                message = result.deletedIds().size() + " deleted; " + result.failedIds().size() + " failed.";
                error = true;
            }
            ListViewState reloaded = reload(state, state.query(), Set.of(), message, error);
            if (reloaded.rows().isEmpty() && reloaded.page() > 1) {
                int page = Math.max(1, reloaded.totalPages());
                reloaded = reload(reloaded, reloaded.query().withPage(page), Set.of(), message, error);
                publishQueryChanges(Map.of(pageQueryParam().name, page == 1 ? "" : String.valueOf(page)));
            }
            publishSelection(Set.of());
            return reloaded;
        } catch (RuntimeException failure) {
            return state.withMessage("Delete failed: " + safeMessage(failure), true);
        }
    }

    private void requestDelete(ListViewState state,
                               Set<String> requestedIds,
                               StateUpdater<ListViewState> stateUpdater) {
        if (state.isBusy() || !canDelete() || !state.capabilities().canDelete()
                || requestedIds == null || requestedIds.isEmpty()) {
            return;
        }
        Set<String> ids = Set.copyOf(requestedIds);
        if (!deleteInFlight.compareAndSet(false, true)) {
            return;
        }
        String pendingMessage = ids.size() == 1
                ? "Deleting 1 item…"
                : "Deleting " + ids.size() + " items…";
        ListViewState deleting = state.withStatus(ListStatus.DELETING, pendingMessage);
        stateUpdater.setState(deleting);
        // One extra queue turn lets the busy-state DOM commands reach the client
        // before a synchronous repository implementation starts doing work.
        lookup().enqueueTask(() -> lookup().enqueueTask(() -> {
            try {
                stateUpdater.setState(deleteAndReload(deleting, ids).withStatus(ListStatus.READY));
                lookup().enqueueTask(() -> deleteInFlight.set(false));
            } catch (RuntimeException | Error failure) {
                deleteInFlight.set(false);
                throw failure;
            }
        }));
    }

    private void refreshFromContext(StateUpdater<ListViewState> stateUpdater) {
        stateUpdater.applyStateTransformation(current -> {
            if (current.isBusy()) {
                return current;
            }
            ListQuery query = resolveQuery(lookup(), current.schema());
            boolean queryChanged = !current.query().equals(query);
            Set<String> selected = queryChanged ? Set.of() : current.selectedIds();
            ListViewState updated = reload(current, query, selected, current.message(), current.error());
            if (queryChanged) {
                publishSelection(Set.of());
            }
            return updated;
        });
    }

    private ListViewState reload(ListViewState current,
                                 ListQuery query,
                                 Set<String> selectedIds,
                                 String message,
                                 boolean error) {
        return load(current, current.schema(), query, selectedIds, current.title(), current.modulePath(),
                current.editTarget(), current.relatedListColumns(), message, error);
    }

    private ListViewState load(ListViewState current,
                               DataSchema schema,
                               ListQuery query,
                               Set<String> selectedIds,
                               String title,
                               String modulePath,
                               ListView.EditTarget editTarget,
                               List<ListView.RelatedListColumn> relatedListColumns,
                               String message,
                               boolean error) {
        ListCapabilities capabilities = current == null
                ? new ListCapabilities(rowKey(), canCreate(), canEdit(), canDelete())
                : current.capabilities();
        try {
            ListPage<T> page = items(query);
            validatePage(query, page);
            int totalPages = page.totalPages(query.pageSize());
            if (totalPages > 0 && query.page() > totalPages) {
                query = query.withPage(totalPages);
                page = items(query);
                validatePage(query, page);
            }
            List<Map<String, Object>> rows = schema.toMapList(page.items());
            validateRowKeys(rows, capabilities.rowKey());
            return new ListViewState(rows, schema, query, page.totalItems(), modulePath, selectedIds,
                    title, editTarget, capabilities, message, error, ListStatus.READY, relatedListColumns);
        } catch (RuntimeException failure) {
            long previousTotal = current == null ? 0 : current.totalItems();
            return new ListViewState(List.of(), schema, query, previousTotal, modulePath, Set.of(), title,
                    editTarget, capabilities, "Could not load items: " + safeMessage(failure), true,
                    ListStatus.READY, relatedListColumns);
        }
    }

    private ListQuery resolveQuery(Lookup source, DataSchema schema) {
        int configuredPageSize = source.getInt(CONFIG_DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZE_FALLBACK);
        int page = safeInteger(source.get(ContextKeys.URL_QUERY.with(pageQueryParam().name)), 1);
        int pageSize = normalizePageSize(
                safeInteger(source.get(ContextKeys.URL_QUERY.with(QUERY_PAGE_SIZE)), configuredPageSize),
                configuredPageSize);
        String sortValue = stringValue(source.get(ContextKeys.URL_QUERY.with(QUERY_SORT_FIELD)));
        String directionValue = stringValue(source.get(ContextKeys.URL_QUERY.with(QUERY_SORT_DIRECTION)));
        SortSpec fallback = defaultSort();
        SortDirection direction = SortDirection.parse(directionValue,
                fallback == null ? SortDirection.ASC : fallback.direction());
        String sortField = sortValue;
        if ("asc".equalsIgnoreCase(sortValue) || "desc".equalsIgnoreCase(sortValue)) {
            direction = SortDirection.parse(sortValue, direction);
            sortField = fallback == null ? "" : fallback.field();
        }
        SortSpec requested = sortField == null || sortField.isBlank() ? fallback : new SortSpec(sortField, direction);
        SortSpec sort = normalizeSort(requested, schema);
        String search = stringValue(source.get(ContextKeys.URL_QUERY.with(QUERY_SEARCH)));
        Map<String, String> filters = new LinkedHashMap<>();
        for (FieldDef field : filterableColumns(schema)) {
            String value = stringValue(source.get(ContextKeys.URL_QUERY.with(QUERY_FILTER_PREFIX + field.name())));
            if (value != null && !value.isBlank()) {
                filters.put(field.name(), value);
            }
        }
        return new ListQuery(Math.max(1, page), pageSize, sort, search, filters);
    }

    private SortSpec normalizeSort(SortSpec requested, DataSchema schema) {
        SortSpec fallback = defaultSort();
        if (requested == null || schema.field(requested.field()) == null) {
            return fallback;
        }
        ColumnConfig config = schema.columnConfig(requested.field());
        if (!config.sortable() && (fallback == null || !fallback.field().equals(requested.field()))) {
            return fallback;
        }
        return requested;
    }

    private static Map<String, String> normalizeFilters(Map<String, String> requested, DataSchema schema) {
        if (requested == null || requested.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (FieldDef field : filterableColumns(schema)) {
            String value = requested.get(field.name());
            if (value != null && !value.isBlank()) {
                result.put(field.name(), value.trim());
            }
        }
        return result;
    }

    private void publishSortQuery(ListQuery query) {
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put(pageQueryParam().name, "");
        updates.put(QUERY_SORT_FIELD, query.sort() == null ? "" : query.sort().field());
        updates.put(QUERY_SORT_DIRECTION, query.sort() == null ? "" : query.sort().direction().queryValue());
        publishQueryChanges(updates);
    }

    private void publishCriteriaQuery(DataSchema schema, ListQuery query) {
        Map<String, String> updates = new LinkedHashMap<>();
        updates.put(pageQueryParam().name, "");
        updates.put(QUERY_SEARCH, query.search());
        for (FieldDef field : filterableColumns(schema)) {
            updates.put(QUERY_FILTER_PREFIX + field.name(), query.filters().getOrDefault(field.name(), ""));
        }
        publishQueryChanges(updates);
    }

    private void publishQueryChanges(Map<String, String> updates) {
        Scene scene = lookup().get(ContextKeys.SCENE);
        if (scene != null && scene.effectiveUrl() != null) {
            lookup().publish(SCENE_QUERY_UPDATED_BATCH, new EventKeys.SceneQueryUpdates(updates));
            return;
        }
        updates.forEach((name, value) -> lookup().publish(EventKeys.STATE_UPDATED.with(name),
                new ContextStateComponent.ContextValue.StringValue(value)));
    }

    private void publishSelection(Set<String> selectedIds) {
        lookup().publish(ListBlockEvents.SELECTION_CHANGED, new ListBlockEvents.SelectedItems(selectedIds));
    }

    private static ListViewState withSelection(ListViewState state, Set<String> selectedIds) {
        return new ListViewState(state.rows(), state.schema(), state.query(), state.totalItems(), state.modulePath(),
                selectedIds, state.title(), state.editTarget(), state.capabilities(), state.message(), state.error(),
                state.status(), state.relatedListColumns());
    }

    private List<ListView.RelatedListColumn> resolveRelatedListColumns(Lookup source, DataSchema schema) {
        List<RelatedListLinkSpec> specs = List.copyOf(java.util.Objects.requireNonNull(
                relatedListLinks(), "relatedListLinks"));
        if (specs.isEmpty()) return List.of();
        Composition composition = source.get(ContextKeys.ROUTE_COMPOSITION);
        if (composition == null || composition.router() == null) {
            throw new IllegalStateException("Related-list links require a routed composition");
        }
        Set<String> keys = new LinkedHashSet<>();
        List<ListView.RelatedListColumn> resolved = new java.util.ArrayList<>();
        for (RelatedListLinkSpec spec : specs) {
            if (spec == null) throw new IllegalStateException("Related-list link cannot be null");
            if (!keys.add(spec.key())) {
                throw new IllegalStateException("Duplicate related-list column key: " + spec.key());
            }
            if (schema.field(spec.sourceField()) == null) {
                throw new IllegalStateException("Related-list source field is not present in schema: "
                        + spec.sourceField());
            }
            String targetPath = composition.router().findRoutePattern(spec.targetBlockKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "Related-list target has no route: " + spec.targetBlockKey()));
            if (targetPath.contains(":")) {
                throw new IllegalStateException("Related-list target must be a collection route: " + targetPath);
            }
            resolved.add(new ListView.RelatedListColumn(spec.key(), spec.label(), spec.sourceField(),
                    targetPath, spec.filterField(), spec.linkLabel()));
        }
        return List.copyOf(resolved);
    }

    private static void validateSchema(DataSchema schema) {
        if (schema == null) {
            throw new IllegalStateException("listSchema() cannot return null");
        }
        Set<String> fields = new LinkedHashSet<>();
        for (FieldDef field : schema.fields()) {
            if (!fields.add(field.name())) {
                throw new IllegalStateException("Duplicate schema field: " + field.name());
            }
        }
    }

    private static void validateDefaultSort(DataSchema schema, SortSpec sort) {
        if (sort != null && schema.field(sort.field()) == null) {
            throw new IllegalStateException("Default sort field is not present in list schema: " + sort.field());
        }
    }

    private static void validatePage(ListQuery query, ListPage<?> page) {
        if (page == null) {
            throw new IllegalStateException("items() cannot return null");
        }
        long offset = (long) (query.page() - 1) * query.pageSize();
        if (!page.items().isEmpty() && offset + page.items().size() > page.totalItems()) {
            throw new IllegalStateException("Page rows exceed the exact totalItems boundary");
        }
    }

    private static void validateDeleteResult(Set<String> requestedIds, DeleteResult result) {
        if (result == null) {
            throw new IllegalStateException("bulkDelete() cannot return null");
        }
        Set<String> reported = new LinkedHashSet<>(result.deletedIds());
        reported.addAll(result.failedIds());
        if (!reported.equals(Set.copyOf(requestedIds))) {
            throw new IllegalStateException("DeleteResult must report every requested ID and no others");
        }
    }

    private static void validateRowKeys(List<Map<String, Object>> rows, String rowKey) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get(rowKey);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new IllegalStateException("Missing row key '" + rowKey + "'");
            }
            if (!ids.add(String.valueOf(value))) {
                throw new IllegalStateException("Duplicate row key '" + value + "'");
            }
        }
    }

    private static List<FieldDef> filterableColumns(DataSchema schema) {
        return schema.listColumns().stream()
                .filter(field -> schema.columnConfig(field.name()).filterable())
                .toList();
    }

    private static int safeInteger(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            return safeInteger(list.getFirst(), fallback);
        }
        return fallback;
    }

    private static String stringValue(Object value) {
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof String string) {
            return string;
        }
        return "";
    }

    private static int normalizePageSize(int pageSize, int fallback) {
        int safeFallback = Math.max(1, Math.min(fallback, MAX_PAGE_SIZE));
        return pageSize < 1 || pageSize > MAX_PAGE_SIZE ? safeFallback : pageSize;
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

    private static String safeMessage(RuntimeException failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
    }
}
