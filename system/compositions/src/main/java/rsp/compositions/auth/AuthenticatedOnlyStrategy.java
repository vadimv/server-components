package rsp.compositions.auth;

import rsp.authentication.Authentication;
import rsp.component.Lookup;
import rsp.compositions.block.BlockRuntime;

/**
 * AuthenticatedOnlyStrategy - Requires user to be authenticated.
 * <p>
 * Allows access to any authenticated user, regardless of roles/permissions.
 */
public class AuthenticatedOnlyStrategy implements BlockRuntime.AuthorizationStrategy {

    @Override
    public boolean isAuthorized(BlockRuntime block, Lookup lookup) {
        Authentication authentication = lookup.get(Authentication.class);
        return authentication != null && authentication.isAuthenticated();
    }
}
