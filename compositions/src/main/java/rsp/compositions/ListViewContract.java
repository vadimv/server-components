package rsp.compositions;

import rsp.component.ComponentContext;

import java.util.List;

public abstract class ListViewContract<T> extends ViewContract {

    private final int pageSize;

    protected ListViewContract(ComponentContext context) {
        super(context);
        // Read page size from AppConfig if available, otherwise use default
        AppConfig config = context.get(AppConfig.class);
        this.pageSize = config != null ? config.defaultPageSize() : 10;
    }

    /**
     * Get the configured page size for this list view.
     * This value is read from AppConfig during contract construction.
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
