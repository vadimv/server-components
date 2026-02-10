package rsp.compositions.auth;

import rsp.component.ComponentContext;

/**
 * StubAuthProvider - Simple auth provider for development/testing.
 * <p>
 * Always authenticates users as anonymous.
 * Replace with real authentication in production.
 */
public class StubAuthProvider implements AuthComponent.AuthProvider {

    @Override
    public AuthComponent.AuthResult authenticate(ComponentContext context) {
        // For now, always return anonymous
        // In a real implementation, read from session, cookies, JWT, etc.
        return AuthComponent.AuthResult.anonymous();
    }

    /**
     * Example: Authenticated user with roles
     */
    public static StubAuthProvider withUser(String username, String... roles) {
        return new StubAuthProvider() {
            @Override
            public AuthComponent.AuthResult authenticate(ComponentContext context) {
                return AuthComponent.AuthResult.authenticated(username, roles);
            }
        };
    }
}
