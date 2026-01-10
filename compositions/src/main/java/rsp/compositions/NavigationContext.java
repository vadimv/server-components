package rsp.compositions;

import rsp.component.ComponentContext;
import rsp.page.events.Command;
import rsp.page.events.RemoteCommand;

/**
 * NavigationContext - High-level navigation abstraction.
 * <p>
 * Decouples UI components from low-level RemoteCommand usage.
 * Delegates to contracts for route determination.
 */
public class NavigationContext {

    private final ComponentContext componentContext;

    public NavigationContext(ComponentContext componentContext) {
        this.componentContext = componentContext;
    }

    /**
     * Navigate to the list route associated with the current EditView.
     * <p>
     * Reads the EditViewContract from context and delegates to its listRoute() method.
     * First checks for overlay contract (QUERY_PARAM/MODAL modes), then falls back
     * to primary view contract (SEPARATE_PAGE mode).
     *
     * @return Command to execute the navigation
     */
    public Command navigateToList() {
        // First try overlay contract (for QUERY_PARAM/MODAL modes)
        EditViewContract<?> contract = (EditViewContract<?>) componentContext.get(ContextKeys.OVERLAY_VIEW_CONTRACT);

        // Fall back to primary view contract (for SEPARATE_PAGE mode)
        if (contract == null) {
            ViewContract viewContract = componentContext.get(ContextKeys.VIEW_CONTRACT);
            if (viewContract instanceof EditViewContract<?> editContract) {
                contract = editContract;
            }
        }

        if (contract == null) {
            throw new IllegalStateException("EditViewContract not found in context (checked OVERLAY_VIEW_CONTRACT and VIEW_CONTRACT)");
        }

        String listRoute = contract.listRoute();
        return new RemoteCommand.SetHref(listRoute);
    }

    /**
     * Navigate to a specific path.
     *
     * @param path The target path
     * @return Command to execute the navigation
     */
    public Command navigateTo(String path) {
        return new RemoteCommand.SetHref(path);
    }
}
