package rsp.compositions.auth;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.server.Path;
import rsp.server.http.Header;
import rsp.server.http.HttpMethod;
import rsp.server.http.HttpRequest;
import rsp.server.http.Query;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuthPKCEProviderTests {

    @Test
    void authenticate_rejects_session_after_server_side_expiry() {
        final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        final OAuthPKCEProvider provider = new OAuthPKCEProvider(config(), clock, 1);
        final String token = provider.createSession("alice");

        assertTrue(provider.authenticate(contextWithOAuthCookie(token)).authenticated(),
                "fresh OAuth session should authenticate");

        clock.advanceSeconds(1);

        assertFalse(provider.authenticate(contextWithOAuthCookie(token)).authenticated(),
                "OAuth session should expire server-side with the cookie max age");
    }

    @Test
    void safe_local_redirect_accepts_path_absolute_targets() {
        assertEquals("/posts", OAuthPKCEProvider.safeLocalRedirect("/posts"));
        assertEquals("/posts?page=2#comments", OAuthPKCEProvider.safeLocalRedirect("/posts?page=2#comments"));
    }

    @Test
    void safe_local_redirect_falls_back_for_external_or_header_unsafe_targets() {
        assertEquals("/", OAuthPKCEProvider.safeLocalRedirect(null));
        assertEquals("/", OAuthPKCEProvider.safeLocalRedirect(""));
        assertEquals("/", OAuthPKCEProvider.safeLocalRedirect("https://evil.example/posts"));
        assertEquals("/", OAuthPKCEProvider.safeLocalRedirect("//evil.example/posts"));
        assertEquals("/", OAuthPKCEProvider.safeLocalRedirect("/\\evil.example/posts"));
        assertEquals("/", OAuthPKCEProvider.safeLocalRedirect("/%zz"));
        assertEquals("/", OAuthPKCEProvider.safeLocalRedirect("/posts\r\nSet-Cookie: injected=true"));
        assertEquals("/", OAuthPKCEProvider.safeLocalRedirect("/posts with spaces"));
    }

    private static ComponentContext contextWithOAuthCookie(String token) {
        final HttpRequest request = new HttpRequest(
                HttpMethod.GET,
                URI.create("http://localhost/posts"),
                "http://localhost/posts",
                Path.of("/posts"),
                Query.EMPTY,
                List.of(new Header("Cookie", OAuthPKCEProvider.SESSION_COOKIE_NAME + "=" + token)));
        return new ComponentContext().with(HttpRequest.class, request);
    }

    private static OAuthPKCEProvider.OAuthConfig config() {
        return new OAuthPKCEProvider.OAuthConfig(
                "http://localhost:8084/authorize",
                "http://localhost:8084/token",
                "http://localhost:8084/userinfo",
                "test-client",
                null,
                "http://localhost:8083/auth/callback",
                "/auth/login",
                "/auth/signin",
                "/auth/callback",
                "/auth/signout",
                "openid profile email");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
