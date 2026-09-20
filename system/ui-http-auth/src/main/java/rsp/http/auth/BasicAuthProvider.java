package rsp.http.auth;

import rsp.application.ApplicationLifecycle;
import rsp.authentication.Authentication;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.PageApplication;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
public class BasicAuthProvider implements HttpAuthenticator {

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
    public Authentication authenticate(HttpRequest request) {
        String authHeader = request.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return Authentication.anonymous();
        }

        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Authentication.anonymous();
        }

        int colonIndex = decoded.indexOf(':');
        if (colonIndex < 0) {
            return Authentication.anonymous();
        }

        String username = decoded.substring(0, colonIndex);
        String password = decoded.substring(colonIndex + 1);

        UserEntry entry = credentials.get(username);
        if (entry == null || !entry.password().equals(password)) {
            return Authentication.anonymous();
        }

        return Authentication.authenticated(username, entry.roles());
    }

    public PageApplication pages(ApplicationLifecycle lifecycle, AuthenticatedPageHandler pages) {
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(pages, "pages");
        return PageApplication.withLifecycle(lifecycle, request -> {
            Authentication authentication = authenticate(request);
            if (authentication.isAuthenticated()) {
                return pages.handle(request, authentication);
            }
            return HttpResponse.status(HttpStatus.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Basic realm=\"" + realm + "\"")
                    .build();
        });
    }

    private record UserEntry(String password, String[] roles) {
        private UserEntry {
            Objects.requireNonNull(password, "password");
            roles = Objects.requireNonNull(roles, "roles").clone();
        }

        @Override
        public String[] roles() {
            return roles.clone();
        }
    }
}
