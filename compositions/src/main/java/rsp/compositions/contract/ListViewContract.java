package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.component.definitions.ContextStateComponent;
import rsp.compositions.agent.AgentAction;
import rsp.compositions.agent.ContractMetadata;
import rsp.compositions.agent.PayloadParsers;
import rsp.compositions.schema.DataSchema;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static rsp.compositions.contract.ActionBindings.*;
import static rsp.compositions.contract.EventKeys.SHOW;
import static rsp.compositions.contract.EventKeys.STATE_UPDATED;

public abstract class ListViewContract<T> extends ViewContract {


    public static final EventKey.VoidKey CREATE_ELEMENT_REQUESTED = new EventKey.VoidKey("list.create.element.requested");

    public static final EventKey.SimpleKey<String> EDIT_ELEMENT_REQUESTED = new EventKey.SimpleKey<>("list.edit.element", String.class);
    /**
     * Bulk delete action requested for selected rows.
     * Emitted by: DefaultListView (Delete Selected button)
     * Handled by: ListViewContract.registerHandlers()
     * Payload: Set of row IDs to delete
     */
    @SuppressWarnings("unchecked")
    public static final EventKey.SimpleKey<Set<String>> BULK_DELETE_REQUESTED =
            new EventKey.SimpleKey<>("bulk.delete.requested",
                    (Class<Set<String>>) (Class<?>) Set.class);


    public static final EventKey.SimpleKey<Integer> PAGE_CHANGE_REQUESTED =
            new EventKey.SimpleKey<>("change.requested",
                                      Integer.class);

    /**
     * Select all rows on the current page.
     * Emitted by: agent (via ActionDispatcher)
     * Handled by: DefaultListView.onMounted()
     */
    public static final EventKey.VoidKey SELECT_ALL_REQUESTED =
            new EventKey.VoidKey("list.select.all.requested");

    /**
     * Selection state changed.
     * Emitted by: DefaultListView when selection changes
     * Consumed by: ListViewContract (to enrich edit actions with current selection)
     */
    public static final EventKey.SimpleKey<SelectedItems> SELECTION_CHANGED =
            new EventKey.SimpleKey<>("list.selection.changed", SelectedItems.class);

    /**
     * Immutable payload for selection change events.
     *
     * @param ids the currently selected row IDs
     */
    public record SelectedItems(Set<String> ids) {
        public SelectedItems { ids = Set.copyOf(ids); }
    }

    /**
     * Config key for default page size.
     */
    public static final String CONFIG_DEFAULT_PAGE_SIZE = "list.defaultPageSize";

    private static final int DEFAULT_PAGE_SIZE_FALLBACK = 10;

    private final int pageSize;
    private DataSchema cachedSchema;
    private Set<String> selectedIds = Set.of();

    protected ListViewContract(Lookup lookup) {
        super(lookup);
        this.pageSize = lookup.getInt(CONFIG_DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZE_FALLBACK);
    }

    protected int pageSize() {
        return pageSize;
    }


    protected  abstract QueryParam<Integer>  pageQueryParam();

    public int page() {
        return resolve(pageQueryParam());
    }

    public abstract String sort();

    public abstract List<T> items();

    /**
     * Get schema - auto-extracted from items + customization.
     * Schema is cached after first access to avoid repeated extraction.
     * 
     * @return DataSchema for rendering list columns
     */
    public DataSchema schema() {
        if (cachedSchema == null) {
            List<T> items = items();
            DataSchema baseSchema = items.isEmpty()
                ? new DataSchema(List.of())
                : DataSchema.fromFirstItem(items.get(0));
            cachedSchema = customizeSchema(baseSchema);
        }
        return cachedSchema;
    }

    /**
     * Override to customize the auto-extracted schema.
     * Called only once, before schema is cached.
     *
     * @param schema The auto-extracted schema from first item
     * @return The customized schema (default: returns unchanged)
     */
    protected DataSchema customizeSchema(DataSchema schema) {
        return schema;
    }


    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        return context
            .with(ContextKeys.CONTRACT_CLASS, getClass())
            .with(ContextKeys.LIST_ITEMS, items())
            .with(ContextKeys.LIST_SCHEMA, schema())
            .with(ContextKeys.LIST_PAGE, page())
            .with(ContextKeys.LIST_SORT, sort())
            .with(ContextKeys.CONTRACT_TITLE, title());
    }

    @Override
    protected void registerHandlers() {
        // Publish active category capability for sibling contracts (e.g., header)
        publishCapability(Capabilities.ACTIVE_CATEGORY, title());

        // Track selection changes from the list view component
        subscribe(SELECTION_CHANGED, (_, selectionEvent) -> {
            selectedIds = selectionEvent.ids();
        });

        // Handle bulk delete requests
        subscribe(BULK_DELETE_REQUESTED, (name, bulkDeleteIds) -> {
            handleBulkDelete(bulkDeleteIds);
        });

        subscribe(PAGE_CHANGE_REQUESTED, (name, newPage) -> {
            lookup.publish(STATE_UPDATED.with(pageQueryParam().name),
                    new ContextStateComponent.ContextValue.StringValue(String.valueOf(newPage)));
        });

        subscribe(CREATE_ELEMENT_REQUESTED, () -> {
            lookup.publish(SHOW, new ShowPayload(
                    createElementContract(),
                    Map.of()
            ));
        });

        subscribe(EDIT_ELEMENT_REQUESTED, (_, rowId) -> {
            lookup.publish(SHOW, new ShowPayload(
                    editElementContract(),
                    Map.of("id", rowId)
            ));
        });
    }

    protected abstract Class<? extends ViewContract> createElementContract();


    protected abstract Class<? extends ViewContract> editElementContract();

    /**
     * Handle bulk delete request for selected items.
     * Default implementation calls bulkDelete() and refreshes the list on success.
     *
     * @param selectedIds Set of row IDs to delete
     */
    protected void handleBulkDelete(Set<String> selectedIds) {
        int deletedCount = bulkDelete(selectedIds);
        if (deletedCount > 0) {
            onBulkDeleteSuccess(deletedCount);
        } else {
            onBulkDeleteFailure(selectedIds);
        }
    }

    /**
     * Delete multiple items by their IDs.
     * <p>
     * Subclasses should override to provide actual deletion logic.
     * Default throws UnsupportedOperationException.
     *
     * @param ids Set of item IDs to delete
     * @return Number of items successfully deleted
     */
    protected int bulkDelete(Set<String> ids) {
        throw new UnsupportedOperationException("Bulk delete not implemented. Override bulkDelete() in your contract.");
    }

    /**
     * Called after successful bulk delete.
     * Emits ACTION_SUCCESS event - framework derives behavior from composition config.
     * <p>
     * This follows the CountersMainComponent pattern:
     * <ul>
     *   <li>Contract emits INTENT (action type only, no routes)</li>
     *   <li>Framework derives behavior from composition configuration</li>
     *   <li>Since this is the primary contract, framework will rebuild scene (refresh in place)</li>
     * </ul>
     *
     * @param deletedCount Number of items deleted
     */
    protected void onBulkDeleteSuccess(int deletedCount) {
        // Emit generic success event - framework derives behavior from composition
        // Since this contract IS the primary, framework will rebuild scene (refresh in place)
        lookup.publish(EventKeys.ACTION_SUCCESS,
            new EventKeys.ActionResult(getClass()));
    }

    /**
     * Called when bulk delete fails.
     * Default does nothing - override for custom error handling.
     *
     * @param failedIds IDs that failed to delete
     */
    protected void onBulkDeleteFailure(Set<String> failedIds) {
        // Default: silent failure - override for error handling
    }

    @Override
    public Object enrichPayload(AgentAction action, Object rawPayload) {
        if (rawPayload != null || selectedIds.isEmpty()) {
            return rawPayload;
        }
        return switch (action.action()) {
            case "edit" -> selectedIds.iterator().next();
            case "delete" -> selectedIds;
            default -> rawPayload;
        };
    }

    @Override
    public List<AgentAction> agentActions() {
        return List.of(
            new AgentAction("create", CREATE_ELEMENT_REQUESTED,
                "Open create form for a new item", null),
            new AgentAction("edit", EDIT_ELEMENT_REQUESTED,
                "Open edit form for an item", "String: row ID",
                PayloadParsers.toStringPayload()),
            new AgentAction("delete", BULK_DELETE_REQUESTED,
                "Delete items by their IDs", "Set<String>: row IDs",
                PayloadParsers.toSetOfStrings()),
            new AgentAction("page", PAGE_CHANGE_REQUESTED,
                "Navigate to a page number", "Integer: page number (1-based)",
                PayloadParsers.toInteger()),
            new AgentAction("select_all", SELECT_ALL_REQUESTED,
                "Select all rows on the current page", null)
        );
    }

    @Override
    public ContractMetadata contractMetadata() {
        return new ContractMetadata(title(),
            "Paginated data list",
            schema(),
            Map.of("page", page(), "pageSize", pageSize(), "sort", sort(),
                   "items", schema().toMapList(items())));
    }
}
