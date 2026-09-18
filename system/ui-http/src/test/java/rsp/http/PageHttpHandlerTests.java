package rsp.http;

import org.junit.jupiter.api.Test;
import rsp.component.definitions.Component;
import rsp.component.definitions.StatelessComponent;
import rsp.component.View;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderedPage;
import rsp.url.Path;
import rsp.url.Query;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.dsl.Html.body;
import static rsp.dsl.Html.head;
import static rsp.dsl.Html.html;
import static rsp.dsl.Html.title;

class PageHttpHandlerTests {
    @Test
    void static_pages_can_set_http_status_and_headers_without_live_bootstrap() throws Exception {
        Map<QualifiedSessionId, RenderedPage> sessions = new ConcurrentHashMap<>();
        PageHttpHandler handler = handler(sessions, request -> Pages.staticHtml(page())
                .status(HttpStatus.NOT_FOUND)
                .header("X-Page", "static"));

        HttpResponse response = handler.handle(request("/missing")).join();
        String html = new String(response.body().openStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(HttpStatus.NOT_FOUND, response.status());
        assertEquals("static", response.headers().first("x-page").orElseThrow());
        assertFalse(html.contains(PageHttpHandler.JS_CLIENT_BUNDLE_PATH));
        assertTrue(sessions.isEmpty());
    }

    @Test
    void live_pages_receive_bootstrap_and_are_registered_for_websocket_binding() throws Exception {
        Map<QualifiedSessionId, RenderedPage> sessions = new ConcurrentHashMap<>();
        PageHttpHandler handler = handler(sessions, request -> Pages.live(page()));

        HttpResponse response = handler.handle(request("/")).join();
        String html = new String(response.body().openStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(HttpStatus.OK, response.status());
        assertTrue(html.contains(PageHttpHandler.JS_CLIENT_BUNDLE_PATH));
        assertEquals(1, sessions.size());
        assertEquals(1, response.headers().all("Set-Cookie").size());
    }

    @Test
    void redirects_and_direct_responses_bypass_component_rendering() {
        PageHttpHandler redirectHandler = handler(new ConcurrentHashMap<>(),
                request -> Pages.redirect("/login").header("X-Reason", "auth"));
        HttpResponse redirect = redirectHandler.handle(request("/private")).join();

        assertEquals(HttpStatus.FOUND, redirect.status());
        assertEquals("/login", redirect.headers().first("Location").orElseThrow());
        assertEquals("auth", redirect.headers().first("X-Reason").orElseThrow());

        PageHttpHandler responseHandler = handler(new ConcurrentHashMap<>(),
                request -> Pages.response(HttpResponse.status(HttpStatus.FORBIDDEN).text("no").build()));
        assertEquals(HttpStatus.FORBIDDEN, responseHandler.handle(request("/private")).join().status());
    }

    private static PageHttpHandler handler(Map<QualifiedSessionId, RenderedPage> sessions,
                                           PageApplication application) {
        return new PageHttpHandler(sessions, application, 10_000);
    }

    private static Component<?, ?> page() {
        return new StatelessComponent((View<StatelessComponent.Unit>) _ ->
                html(head(title("test")), body()));
    }

    private static HttpRequest request(String target) {
        URI uri = URI.create(target);
        return new HttpRequest(HttpMethod.GET, target, uri.getRawPath(), uri,
                "http://localhost" + target, Path.parse(uri.getRawPath()),
                uri.getRawQuery() == null ? Query.EMPTY : Query.parse(uri.getRawQuery()),
                HttpHeaders.EMPTY, RequestBody.EMPTY);
    }
}
