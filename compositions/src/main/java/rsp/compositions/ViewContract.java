package rsp.compositions;

import rsp.component.ComponentContext;

import java.util.Set;

public abstract class ViewContract {

    protected final ComponentContext context;

    protected ViewContract(ComponentContext context) {
        this.context = context;
    }

    protected <T> T resolve(QueryParam<T> param) {
        return param.resolve(context);
    }

    public abstract String name();

    /**
     * Override to specify required roles for accessing this contract.
     * Default: no restrictions (empty set).
     *
     * @return Set of role names required to access this view
     */
    public Set<String> requiredRoles() {
        return Set.of();
    }

    /**
     * Helper to check if current user has required authentication/authorization.
     *
     * @return true if user is authorized, false otherwise
     */
    protected boolean isAuthorized() {
        Boolean authenticated = (Boolean) context.getAttribute("auth.authenticated");
        if (authenticated == null || !authenticated) {
            return requiredRoles().isEmpty(); // Only allow if no roles required
        }

        Set<String> required = requiredRoles();
        if (required.isEmpty()) {
            return true; // No specific roles required, just need to be authenticated
        }

        String[] userRoles = (String[]) context.getAttribute("auth.roles");
        if (userRoles == null) {
            return false;
        }

        // Check if user has any of the required roles
        for (String userRole : userRoles) {
            if (required.contains(userRole)) {
                return true;
            }
        }

        return false;
    }
}
