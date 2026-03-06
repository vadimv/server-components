package rsp.compositions.auth;

import rsp.component.ComponentContext;
import rsp.dsl.Definition;
import rsp.server.http.HttpRequest;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static rsp.dsl.Html.html;

/**
 * BasicAuthProvider - HTTP Basic Authentication.
 * <p>
 * Reads the {@code Authorization: Basic ...} header from each request.
 * When missing or invalid, returns a 401 response with {@code WWW-Authenticate} header,
 * which causes the browser to show its native credentials dialog.
 * <p>
 * Credentials are validated against an in-memory map.
 * The browser resends credentials on every request — no server-side session needed.
 */
public class BasicAuthProvider implements AuthComponent.AuthProvider {

    private final String realm;
    private final Map<String, UserEntry> credentials = new ConcurrentHashMap<>();

    public BasicAuthProvider(String realm) {
        this.realm = realm;
    }

    public BasicAuthProvider() {
        this("rsp");
    }

    /**
     * Registers a user with the given password and roles.
     */
    public BasicAuthProvider user(String username, String password, String... roles) {
        credentials.put(username, new UserEntry(password, roles));
        return this;
    }

    @Override
    public AuthComponent.AuthResult authenticate(ComponentContext context) {
        HttpRequest request = context.get(HttpRequest.class);
        if (request == null) {
            return AuthComponent.AuthResult.anonymous();
        }

        String authHeader = request.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return AuthComponent.AuthResult.anonymous();
        }

        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)));
        } catch (IllegalArgumentException e) {
            return AuthComponent.AuthResult.anonymous();
        }

        int colonIndex = decoded.indexOf(':');
        if (colonIndex < 0) {
            return AuthComponent.AuthResult.anonymous();
        }

        String username = decoded.substring(0, colonIndex);
        String password = decoded.substring(colonIndex + 1);

        UserEntry entry = credentials.get(username);
        if (entry == null || !entry.password().equals(password)) {
            return AuthComponent.AuthResult.anonymous();
        }

        return AuthComponent.AuthResult.authenticated(username, entry.roles());
    }

    @Override
    public Definition gateResponse(String currentPath) {
        return html().statusCode(401).addHeader("WWW-Authenticate", "Basic realm=\"" + realm + "\"");
    }

    private record UserEntry(String password, String[] roles) {}
}
