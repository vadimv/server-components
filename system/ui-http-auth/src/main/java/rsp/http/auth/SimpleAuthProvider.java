package rsp.http.auth;

import rsp.component.CommandsEnqueue;
import rsp.compositions.auth.AuthComponent;
import rsp.compositions.auth.LoginBlock;
import rsp.compositions.application.App;
import rsp.page.events.RemoteCommand;
import rsp.http.HttpRequest;
import rsp.http.PageApplication;
import rsp.http.Pages;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SimpleAuthProvider - Cookie-based authentication with in-memory session store.
 * <p>
 * On first request: no session cookie → returns anonymous (gate redirects to login).
 * After login: session cookie is set → returns authenticated.
 * <p>
 * Sessions are stored in-memory (lost on server restart).
 */
public class SimpleAuthProvider implements AuthComponent.AuthProvider, LoginBlock.DemoSessionProvider {

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

    public AuthComponent.AuthResult authenticate(HttpRequest request) {
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

    public PageApplication pages(App app) {
        Objects.requireNonNull(app, "app");
        return request -> {
            AuthComponent.AuthResult identity = authenticate(request);
            String currentPath = request.path().toString();
            if (identity.authenticated() || currentPath.startsWith("/auth")) {
                return Pages.live(app.apply(request.relativeUrl(), identity));
            }
            return Pages.redirect("/auth/login?redirect=" + currentPath);
        };
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

    @Override
    public String cookieName() {
        return SESSION_COOKIE_NAME;
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
