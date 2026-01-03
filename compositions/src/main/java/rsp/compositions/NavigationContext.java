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
     *
     * @return Command to execute the navigation
     */
    public Command navigateToList() {
        EditViewContract<?> contract = (EditViewContract<?>) componentContext.getAttribute("view.contract");

        if (contract == null) {
            throw new IllegalStateException("view.contract not found in context");
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
