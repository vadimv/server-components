package rsp.http.auth;

import org.junit.jupiter.api.Test;
import rsp.url.Path;
import rsp.http.HttpHeader;
import rsp.http.HttpHeaders;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.RequestBody;
import rsp.url.Query;

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

        assertTrue(provider.authenticate(contextWithOAuthCookie(token)).isAuthenticated(),
                "fresh OAuth session should authenticate");

        clock.advanceSeconds(1);

        assertFalse(provider.authenticate(contextWithOAuthCookie(token)).isAuthenticated(),
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

    private static HttpRequest contextWithOAuthCookie(String token) {
        return new HttpRequest(
                HttpMethod.GET,
                "/posts",
                "/posts",
                URI.create("http://localhost/posts"),
                "http://localhost/posts",
                Path.of("/posts"),
                Query.EMPTY,
                HttpHeaders.of(new HttpHeader("Cookie", OAuthPKCEProvider.SESSION_COOKIE_NAME + "=" + token)),
                RequestBody.EMPTY);
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
