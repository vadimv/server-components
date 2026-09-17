package rsp.http.auth;

import org.junit.jupiter.api.Test;
import rsp.compositions.application.App;
import rsp.application.ApplicationContext;
import rsp.http.HttpHeader;
import rsp.http.HttpHeaders;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.HttpStatus;
import rsp.http.PageResult;
import rsp.http.RequestBody;
import rsp.url.Path;
import rsp.url.Query;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AuthPageAdaptersTests {
    private final App app = new App(ApplicationContext.builder().build(), List.of());

    @Test
    void basic_auth_returns_a_challenge_before_rendering_and_renders_authenticated_requests() {
        BasicAuthProvider provider = new BasicAuthProvider("admin-area").user("alice", "secret", "admin");

        PageResult.Response challenge = assertInstanceOf(PageResult.Response.class,
                provider.pages(app).handle(request("/private")));
        assertEquals(HttpStatus.of(401), challenge.response().status());
        assertEquals("Basic realm=\"admin-area\"",
                challenge.response().headers().first("WWW-Authenticate").orElseThrow());

        String credentials = Base64.getEncoder().encodeToString("alice:secret".getBytes(StandardCharsets.UTF_8));
        assertInstanceOf(PageResult.Render.class,
                provider.pages(app).handle(request("/private", new HttpHeader("Authorization", "Basic " + credentials))));
    }

    @Test
    void session_auth_redirects_private_requests_but_allows_the_login_page() {
        SimpleAuthProvider provider = new SimpleAuthProvider();

        PageResult.Redirect redirect = assertInstanceOf(PageResult.Redirect.class,
                provider.pages(app).handle(request("/private")));
        assertEquals("/auth/login?redirect=/private", redirect.location().toString());
        assertInstanceOf(PageResult.Render.class,
                provider.pages(app).handle(request("/auth/login")));
    }

    @Test
    void authentication_adapter_preserves_application_lifecycle() {
        ApplicationContext context = ApplicationContext.builder().build();
        App managedApp = new App(context, List.of());
        var pages = new SimpleAuthProvider().pages(managedApp);

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
