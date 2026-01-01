package rsp.compositions.auth;

import rsp.component.ComponentContext;
import rsp.compositions.ViewContract;

/**
 * PublicAccessStrategy - Allows all access (no restrictions).
 * <p>
 * Useful for public-facing applications or development/testing.
 */
public class PublicAccessStrategy implements ViewContract.AuthorizationStrategy {

    @Override
    public boolean isAuthorized(ViewContract contract, ComponentContext context) {
        return true; // Always allow
    }
}
