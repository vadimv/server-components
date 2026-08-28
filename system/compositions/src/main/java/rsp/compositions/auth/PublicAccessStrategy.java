package rsp.compositions.auth;

import rsp.component.Lookup;
import rsp.compositions.block.BlockRuntime;

/**
 * PublicAccessStrategy - Allows all access (no restrictions).
 * <p>
 * Useful for public-facing applications or development/testing.
 */
public class PublicAccessStrategy implements BlockRuntime.AuthorizationStrategy {

    @Override
    public boolean isAuthorized(BlockRuntime block, Lookup lookup) {
        return true; // Always allow
    }
}
