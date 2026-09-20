package rsp.http.auth;

import org.junit.jupiter.api.Test;
import rsp.application.ApplicationContext;
import rsp.authentication.Authentication;
import rsp.http.HttpHeader;
import rsp.http.HttpHeaders;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.PageResult;
import rsp.http.RequestBody;
import rsp.url.Path;
import rsp.url.Query;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthPageAdaptersTests {
    @Test
    void basic_auth_returns_a_challenge_before_rendering_and_renders_authenticated_requests() {
        BasicAuthProvider provider = new BasicAuthProvider("admin-area").user("alice", "secret", "admin");
        AtomicReference<Authentication> seen = new AtomicReference<>();
        var pages = provider.pages(ApplicationContext.builder().build(), (request, authentication) -> {
            seen.set(authentication);
            return HttpResponse.ok().build();
        });

        HttpResponse challenge = assertInstanceOf(HttpResponse.class,
                pages.handle(request("/private")));
        assertEquals(HttpStatus.of(401), challenge.status());
        assertEquals("Basic realm=\"admin-area\"",
                challenge.headers().first("WWW-Authenticate").orElseThrow());

        String credentials = Base64.getEncoder().encodeToString("alice:secret".getBytes(StandardCharsets.UTF_8));
        HttpResponse authenticated = assertInstanceOf(HttpResponse.class,
                pages.handle(request("/private", new HttpHeader("Authorization", "Basic " + credentials))));
        assertEquals(HttpStatus.OK, authenticated.status());
        assertEquals("alice", seen.get().principal());
        assertTrue(seen.get().hasRole("admin"));
    }

    @Test
    void session_auth_redirects_private_requests_but_allows_the_login_page() {
        SimpleAuthProvider provider = new SimpleAuthProvider();
        var pages = provider.pages(ApplicationContext.builder().build(),
                (request, authentication) -> HttpResponse.ok().build());

        PageResult.Redirect redirect = assertInstanceOf(PageResult.Redirect.class,
                pages.handle(request("/private")));
        assertEquals("/auth/login?redirect=%2Fprivate", redirect.location().toString());
        assertInstanceOf(HttpResponse.class, pages.handle(request("/auth/login")));
    }

    @Test
    void session_authentication_is_created_and_cleared_by_http_responses() {
        SimpleAuthProvider provider = new SimpleAuthProvider("alice", "admin");
        AtomicReference<Authentication> seen = new AtomicReference<>();
        var pages = provider.pages(ApplicationContext.builder().build(), (request, authentication) -> {
            seen.set(authentication);
            return HttpResponse.ok().build();
        });

        var signIn = provider.routes().handle(request("/auth/signin?redirect=%2Fprivate"))
                .toCompletableFuture().join();
        assertEquals(HttpStatus.FOUND, signIn.status());
        assertEquals("/private", signIn.headers().first("Location").orElseThrow());
        String setCookie = signIn.headers().first("Set-Cookie").orElseThrow();
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        String cookie = setCookie.substring(0, setCookie.indexOf(';'));

        assertInstanceOf(HttpResponse.class,
                pages.handle(request("/private", new HttpHeader("Cookie", cookie))));
        assertEquals("alice", seen.get().principal());

        var signOut = provider.routes().handle(request("/auth/signout", new HttpHeader("Cookie", cookie)))
                .toCompletableFuture().join();
        assertEquals("/auth/login", signOut.headers().first("Location").orElseThrow());
        assertTrue(signOut.headers().first("Set-Cookie").orElseThrow().contains("Max-Age=0"));
    }

    @Test
    void authentication_adapter_preserves_application_lifecycle() {
        ApplicationContext context = ApplicationContext.builder().build();
        var pages = new SimpleAuthProvider().pages(context,
                (request, authentication) -> HttpResponse.ok().build());

        pages.start();
        assertEquals(ApplicationContext.State.RUNNING, context.state());
        pages.stop();
        assertEquals(ApplicationContext.State.STOPPED, context.state());
    }

    private static HttpRequest request(String target, HttpHeader... headers) {
        URI uri = URI.create(target);
        return new HttpRequest(HttpMethod.GET, target, uri.getRawPath(), uri,
                "http://localhost" + target, Path.parse(uri.getRawPath()),
                uri.getRawQuery() == null ? Query.EMPTY : Query.parse(uri.getRawQuery()),
                HttpHeaders.of(headers), RequestBody.EMPTY);
    }
}
