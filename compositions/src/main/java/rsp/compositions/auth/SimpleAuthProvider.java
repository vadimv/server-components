package rsp.compositions.auth;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.dsl.Definition;
import rsp.page.events.RemoteCommand;
import rsp.server.http.HttpRequest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static rsp.dsl.Html.html;

/**
 * SimpleAuthProvider - Cookie-based authentication with in-memory session store.
 * <p>
 * On first request: no session cookie → returns anonymous (gate redirects to login).
 * After login: session cookie is set → returns authenticated.
 * <p>
 * Sessions are stored in-memory (lost on server restart).
 */
public class SimpleAuthProvider implements AuthComponent.AuthProvider {

    public static final String SESSION_COOKIE_NAME = "rsp_session";

    private final ConcurrentMap<String, UserInfo> sessions = new ConcurrentHashMap<>();
    private final String defaultUsername;
    private final String[] defaultRoles;

    public SimpleAuthProvider(String username, String... roles) {
        this.defaultUsername = username;
        this.defaultRoles = roles;
    }

    public SimpleAuthProvider() {
        this("admin", "admin");
    }

    @Override
    public AuthComponent.AuthResult authenticate(ComponentContext context) {
        HttpRequest request = context.get(HttpRequest.class);
        if (request == null) {
            return AuthComponent.AuthResult.anonymous();
        }

        List<String> cookies = request.cookies(SESSION_COOKIE_NAME);
        if (cookies.isEmpty()) {
            return AuthComponent.AuthResult.anonymous();
        }

        String token = cookies.getFirst();
        UserInfo user = sessions.get(token);
        if (user == null) {
            return AuthComponent.AuthResult.anonymous();
        }

        return AuthComponent.AuthResult.authenticated(user.username(), user.roles());
    }

    @Override
    public Definition gateResponse(String currentPath) {
        if (currentPath.startsWith("/auth")) {
            return null; // public path — no gate
        }
        return html().redirect("/auth/login?redirect=" + currentPath);
    }

    @Override
    public boolean supportsSignOut() {
        return true;
    }

    @Override
    public void signOut(CommandsEnqueue commandsEnqueue) {
        commandsEnqueue.offer(new RemoteCommand.EvalJs(0,
                "document.cookie='" + SESSION_COOKIE_NAME + "=;path=/;max-age=0'"));
        commandsEnqueue.offer(new RemoteCommand.SetHref("/auth/login"));
    }

    /**
     * Creates a new session for the default user and returns the session token.
     */
    public String createSession() {
        return createSession(defaultUsername, defaultRoles);
    }

    /**
     * Creates a new session and returns the session token.
     */
    public String createSession(String username, String... roles) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new UserInfo(username, roles));
        return token;
    }

    public record UserInfo(String username, String[] roles) {}
}
