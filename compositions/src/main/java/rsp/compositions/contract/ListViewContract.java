package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.compositions.schema.DataSchema;

import java.util.List;
import java.util.Set;

import static rsp.compositions.contract.ActionBindings.*;
import static rsp.compositions.contract.ContextKeys.LIST_DEFAULT_PAGE_SIZE;
import static rsp.compositions.contract.EventKeys.*;

public abstract class ListViewContract<T> extends ViewContract {

    /**
     * Context key for default page size configuration.
     * Framework-agnostic: contracts don't need to know about AppConfig structure.
     */
    public static final String CONFIG_DEFAULT_PAGE_SIZE = "list.defaultPageSize";

    /**
     * Default page size fallback if no configuration is provided.
     */
    private static final int DEFAULT_PAGE_SIZE_FALLBACK = 10;

    private final int pageSize;
    private DataSchema cachedSchema;

    protected ListViewContract(Lookup lookup) {
        super(lookup);
        // Read page size from generic config context, completely agnostic of AppConfig
        Integer configuredPageSize = lookup.get(LIST_DEFAULT_PAGE_SIZE);
        this.pageSize = configuredPageSize != null ? configuredPageSize : DEFAULT_PAGE_SIZE_FALLBACK;
    }

    /**
     * Get the configured page size for this list view.
     * This value is read from context using a generic string key, making this contract
     * completely independent of any specific configuration class (AppConfig, etc.).
     *
     * @return The page size (number of items per page)
     */
    protected int pageSize() {
        return pageSize;
    }

    public abstract int page();

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
            .with(ContextKeys.LIST_SORT, sort());
    }

    // ========== Action Bindings ==========

    /**
     * Define action bindings for this list contract.
     * <p>
     * Action bindings map abstract action names to concrete contract classes.
     * When a View emits ACTION("edit", data), this contract translates it
     * to SHOW(targetContract, data) based on the bindings defined here.
     * <p>
     * Override in subclasses to bind actions to contracts:
     * <pre>
     * {@code
     * @Override
     * protected ActionBindings actionBindings() {
     *     return ActionBindings.builder()
     *         .bind("edit", PostEditContract.class)
     *         .bind("create", PostCreateContract.class)
     *         .build();
     * }
     * }
     * </pre>
     *
     * @return ActionBindings for this contract (default: empty)
     */
    protected ActionBindings actionBindings() {
        return ActionBindings.empty();
    }

    // ========== Event Handlers ==========

    @Override
    protected void registerHandlers() {
        // Handle bulk delete requests
        lookup.subscribe(BULK_DELETE_REQUESTED, (name, selectedIds) -> {
            handleBulkDelete(selectedIds);
        });

        // Translate abstract ACTION events to SHOW events via action bindings
        lookup.subscribe(ACTION, (name, payload) -> {
            ActionBindings bindings = actionBindings();
            ActionBinding binding = bindings.get(payload.actionName());
            if (binding != null) {
                // Translate to SHOW event with target contract and data
                lookup.publish(SHOW, new ShowPayload(
                    binding.targetContract(),
                    payload.data()
                ));
            }
        });
    }

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
     * Emits ACTION_SUCCESS event - framework decides what to do based on placement.
     * <p>
     * This enables complete separation of concerns:
     * <ul>
     *   <li>Contract emits generic success (no placement knowledge)</li>
     *   <li>Framework (SceneComponent) handles navigation based on slot</li>
     * </ul>
     *
     * @param deletedCount Number of items deleted
     */
    protected void onBulkDeleteSuccess(int deletedCount) {
        // Emit generic success event - framework decides what to do
        // For bulk delete, target route is current list route (refresh in place)
        String currentPath = lookup.get(ContextKeys.ROUTE_PATH);
        lookup.publish(EventKeys.ACTION_SUCCESS,
            new EventKeys.ActionResult(getClass(), EventKeys.ActionType.DELETE, currentPath));
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
}
