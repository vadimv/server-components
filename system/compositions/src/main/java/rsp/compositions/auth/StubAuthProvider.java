package rsp.compositions.auth;

/**
 * StubAuthProvider - Simple auth provider for development/testing.
 * <p>
 * Always authenticates users as anonymous.
 * Replace with real authentication in production.
 */
public class StubAuthProvider implements AuthComponent.AuthProvider {
    private final AuthComponent.AuthResult result;

    public StubAuthProvider() {
        this(AuthComponent.AuthResult.anonymous());
    }

    private StubAuthProvider(AuthComponent.AuthResult result) {
        this.result = result;
    }

    public AuthComponent.AuthResult result() {
        return result;
    }

    /**
     * Example: Authenticated user with roles
     */
    public static StubAuthProvider withUser(String username, String... roles) {
        return new StubAuthProvider(AuthComponent.AuthResult.authenticated(username, roles));
    }
}
