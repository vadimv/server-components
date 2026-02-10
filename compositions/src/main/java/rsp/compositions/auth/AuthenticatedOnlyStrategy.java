package rsp.compositions.auth;

import rsp.component.Lookup;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.ViewContract;

/**
 * AuthenticatedOnlyStrategy - Requires user to be authenticated.
 * <p>
 * Allows access to any authenticated user, regardless of roles/permissions.
 */
public class AuthenticatedOnlyStrategy implements ViewContract.AuthorizationStrategy {

    @Override
    public boolean isAuthorized(ViewContract contract, Lookup lookup) {
        Boolean authenticated = lookup.get(ContextKeys.AUTH_AUTHENTICATED);
        return Boolean.TRUE.equals(authenticated);
    }
}
