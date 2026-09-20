package rsp.http.auth;

import rsp.application.ApplicationLifecycle;
import rsp.authentication.Authentication;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.PageApplication;
import rsp.http.PageResult;
import rsp.http.SetCookie;
import rsp.http.routing.HttpRouteHandler;
import rsp.http.routing.HttpRouter;

import java.time.Duration;
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
public class SimpleAuthProvider implements HttpAuthenticator {

    public static final String SESSION_COOKIE_NAME = "rsp_session";
    public static final String LOGIN_PATH = "/auth/login";
    public static final String SIGN_IN_PATH = "/auth/signin";
    public static final String SIGN_OUT_PATH = "/auth/signout";

    private final ConcurrentMap<String, UserInfo> sessions = new ConcurrentHashMap<>();
    private final String defaultUsername;
    private final String[] defaultRoles;
    private final HttpRouter routes;

    public SimpleAuthProvider(String username, String... roles) {
        this.defaultUsername = Objects.requireNonNull(username, "username");
        this.defaultRoles = Objects.requireNonNull(roles, "roles").clone();
        this.routes = HttpRouter.builder()
                .get(SIGN_IN_PATH, HttpRouteHandler.sync((request, _) -> handleSignIn(request)))
                .get(SIGN_OUT_PATH, HttpRouteHandler.sync((request, _) -> handleSignOut(request)))
                .build();
    }

    public SimpleAuthProvider() {
        this("admin", "admin");
    }

    @Override
    public Authentication authenticate(HttpRequest request) {
        List<String> cookies = request.cookies(SESSION_COOKIE_NAME);
        if (cookies.isEmpty()) {
            return Authentication.anonymous();
        }

        String token = cookies.getFirst();
        UserInfo user = sessions.get(token);
        if (user == null) {
            return Authentication.anonymous();
        }

        return Authentication.authenticated(user.username(), user.roles());
    }

    public PageApplication pages(ApplicationLifecycle lifecycle, AuthenticatedPageHandler pages) {
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(pages, "pages");
        return PageApplication.withLifecycle(lifecycle, request -> {
            String currentPath = request.path().toString();
            Authentication authentication = authenticate(request);
            if (authentication.isAuthenticated() || currentPath.equals(LOGIN_PATH)) {
                return pages.handle(request, authentication);
            }
            return PageResult.redirect(AuthenticationSupport.loginRedirect(
                    LOGIN_PATH, request.relativeUrl().toString()));
        });
    }

    @Override
    public HttpRouter routes() {
        return routes;
    }

    public String loginPath() {
        return LOGIN_PATH;
    }

    public String signInPath() {
        return SIGN_IN_PATH;
    }

    public String signOutPath() {
        return SIGN_OUT_PATH;
    }

    private String createSession(String username, String... roles) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new UserInfo(username, roles));
        return token;
    }

    private HttpResponse handleSignIn(HttpRequest request) {
        String target = AuthenticationSupport.safeLocalRedirect(
                request.query().parameterValue("redirect"));
        String token = createSession(defaultUsername, defaultRoles);
        SetCookie cookie = SetCookie.of(SESSION_COOKIE_NAME, token)
                .path("/")
                .withHttpOnly()
                .sameSite(SetCookie.SameSite.LAX);
        return HttpResponse.status(HttpStatus.FOUND)
                .header("Location", target)
                .cookie(cookie)
                .build();
    }

    private HttpResponse handleSignOut(HttpRequest request) {
        List<String> cookies = request.cookies(SESSION_COOKIE_NAME);
        if (!cookies.isEmpty()) {
            sessions.remove(cookies.getFirst());
        }
        SetCookie expired = SetCookie.of(SESSION_COOKIE_NAME, "")
                .path("/")
                .maxAge(Duration.ZERO)
                .withHttpOnly()
                .sameSite(SetCookie.SameSite.LAX);
        return HttpResponse.status(HttpStatus.FOUND)
                .header("Location", LOGIN_PATH)
                .cookie(expired)
                .build();
    }

    private record UserInfo(String username, String[] roles) {
        private UserInfo {
            Objects.requireNonNull(username, "username");
            roles = Objects.requireNonNull(roles, "roles").clone();
        }

        @Override
        public String[] roles() {
            return roles.clone();
        }
    }
}
