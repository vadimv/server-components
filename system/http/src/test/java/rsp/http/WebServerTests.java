package rsp.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rsp.component.definitions.Component;
import rsp.component.definitions.StatelessComponent;
import rsp.component.definitions.StatelessComponent.Unit;
import rsp.server.StaticResources;
import rsp.server.http.HttpRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static java.net.http.HttpRequest.BodyPublishers;
import static java.net.http.HttpResponse.BodyHandlers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static rsp.dsl.Html.HeadType.PLAIN;
import static rsp.dsl.Html.body;
import static rsp.dsl.Html.h1;
import static rsp.dsl.Html.head;
import static rsp.dsl.Html.html;
import static rsp.dsl.Html.p;
import static rsp.dsl.Html.title;

class WebServerTests {
    private final HttpClient client = HttpClient.newHttpClient();

    @TempDir
    private Path tempDir;

    @Test
    void serves_rendered_page_on_random_port() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("Hello from http")));
        try {
            assertTrue(server.port() > 0);

            final HttpResponse<String> response = client.send(get(server, "/hello?name=Codex"),
                                                              BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Hello from http"));
            assertTrue(response.headers().firstValue("set-cookie").orElse("").contains("deviceId="));
        } finally {
            server.stop();
        }
    }

    @Test
    void merges_urlencoded_post_body_into_query_parameters() throws Exception {
        final WebServer server = started(new WebServer(0, request ->
                page(request.queryParameters.parameterValue("firstname") + " "
                     + request.queryParameters.parameterValue("lastname"))));
        try {
            final java.net.http.HttpRequest post = java.net.http.HttpRequest.newBuilder(uri(server, "/form"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(BodyPublishers.ofString("firstname=Ada&lastname=Lovelace"))
                    .build();

            final HttpResponse<String> response = client.send(post, BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Ada Lovelace"));
        } finally {
            server.stop();
        }
    }

    @Test
    void serves_static_resources() throws Exception {
        Files.writeString(tempDir.resolve("style.css"), "body { color: red; }");
        final WebServer server = started(new WebServer(0,
                                                       _ -> page("not static"),
                                                       new StaticResources(tempDir.toFile(), "/res/")));
        try {
            final HttpResponse<String> response = client.send(get(server, "/res/style.css"),
                                                              BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("text/css", response.headers().firstValue("content-type").orElse(null));
            assertEquals("body { color: red; }", response.body());
        } finally {
            server.stop();
        }
    }

    @Test
    void serves_js_client_bundle_from_runtime_dependency() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("bundle")));
        try {
            final HttpResponse<String> response = client.send(get(server, "/static/js-client.min.js"),
                                                              BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("application/javascript", response.headers().firstValue("content-type").orElse(null));
            assertTrue(response.body().length() > 100);
        } finally {
            server.stop();
        }
    }

    @Test
    void head_request_omits_response_body() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("head body")));
        try {
            final java.net.http.HttpRequest head = java.net.http.HttpRequest.newBuilder(uri(server, "/head"))
                    .method("HEAD", BodyPublishers.noBody())
                    .build();

            final HttpResponse<String> response = client.send(head, BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("", response.body());
        } finally {
            server.stop();
        }
    }

    @Test
    void unsupported_methods_return_405() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("delete")));
        try {
            final java.net.http.HttpRequest delete = java.net.http.HttpRequest.newBuilder(uri(server, "/delete"))
                    .DELETE()
                    .build();

            final HttpResponse<String> response = client.send(delete, BodyHandlers.ofString());

            assertEquals(405, response.statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void rejects_non_positive_connection_limit() {
        assertThrows(IllegalArgumentException.class,
                     () -> new WebServer(0,
                                         _ -> page("limit"),
                                         Optional.empty(),
                                         Optional.empty(),
                                         0));
    }

    private static WebServer started(final WebServer server) {
        server.start();
        return server;
    }

    private static java.net.http.HttpRequest get(final WebServer server, final String path) {
        return java.net.http.HttpRequest.newBuilder(uri(server, path)).GET().build();
    }

    private static URI uri(final WebServer server, final String path) {
        return URI.create("http://localhost:" + server.port() + path);
    }

    private static Component<?, ?> page(final String text) {
        return new StatelessComponent((rsp.component.View<Unit>) _ -> html(head(PLAIN, title("HTTP test")),
                                                                           body(h1(text), p("served"))));
    }
}
