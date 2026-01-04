package rsp.compositions.auth;

import rsp.component.ComponentContext;
import rsp.compositions.ContextKeys;
import rsp.compositions.ViewContract;

/**
 * AuthenticatedOnlyStrategy - Requires user to be authenticated.
 * <p>
 * Allows access to any authenticated user, regardless of roles/permissions.
 */
public class AuthenticatedOnlyStrategy implements ViewContract.AuthorizationStrategy {

    @Override
    public boolean isAuthorized(ViewContract contract, ComponentContext context) {
        Boolean authenticated = context.get(ContextKeys.AUTH_AUTHENTICATED);
        return Boolean.TRUE.equals(authenticated);
    }
}
