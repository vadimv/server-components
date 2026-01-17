package rsp.compositions.auth;

import rsp.component.Lookup;
import rsp.compositions.ViewContract;

/**
 * PublicAccessStrategy - Allows all access (no restrictions).
 * <p>
 * Useful for public-facing applications or development/testing.
 */
public class PublicAccessStrategy implements ViewContract.AuthorizationStrategy {

    @Override
    public boolean isAuthorized(ViewContract contract, Lookup lookup) {
        return true; // Always allow
    }
}
