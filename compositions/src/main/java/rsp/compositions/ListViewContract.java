package rsp.compositions;

import rsp.component.ComponentContext;

import java.util.List;

import static rsp.compositions.ContextKeys.LIST_DEFAULT_PAGE_SIZE;

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

    protected ListViewContract(ComponentContext context) {
        super(context);
        // Read page size from generic config context, completely agnostic of AppConfig
        Integer configuredPageSize = context.get(LIST_DEFAULT_PAGE_SIZE);
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
}
