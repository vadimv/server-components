package rsp.compositions.auth;

import rsp.component.Lookup;
import rsp.compositions.contract.Contract;

/**
 * PublicAccessStrategy - Allows all access (no restrictions).
 * <p>
 * Useful for public-facing applications or development/testing.
 */
public class PublicAccessStrategy implements Contract.AuthorizationStrategy {

    @Override
    public boolean isAuthorized(Contract contract, Lookup lookup) {
        return true; // Always allow
    }
}
