package rsp.component;

import org.junit.jupiter.api.Test;
import rsp.component.definitions.HttpRequestStateComponent;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.server.Path;
import rsp.server.TestSessonEventsConsumer;
import rsp.server.http.Header;
import rsp.server.http.HttpRequest;
import rsp.server.http.Query;

import java.net.URI;
import java.util.List;

import static rsp.html.HtmlDsl.div;
import static rsp.html.HtmlDsl.span;
import static rsp.util.HtmlAssertions.assertHtmlFragmentsEqual;

class HttpRequestStateComponentSegmentDefinitionTests {

    static final ComponentView<String> view = newState -> state ->
            div(
                    span(state)
            );

    @Test
    void component_renders_initial_html_from_http_request() {
        final QualifiedSessionId qualifiedSessionId = new QualifiedSessionId("test-device", "test-session");
        final URI uri = URI.create("http://localhost/state-0");
        final HttpRequest httpRequest = new HttpRequest(HttpRequest.HttpMethod.GET,
                                                        uri,
                                                        uri.toString(),
                                                        Path.of(uri.getPath()),
                                                        Query.EMPTY,
                                                        List.of(new Header("header-0", "header-0-value")));
        final TestSessonEventsConsumer commands = new TestSessonEventsConsumer();
        final ComponentRenderContext renderContext = new ComponentRenderContext(qualifiedSessionId,
                                                                                TreePositionPath.of("1"),
                                                                                new ComponentContext(),
                                                                                commands);
        final HttpRequestStateComponent<String> scd = new HttpRequestStateComponent<>(httpRequest,
                                                                                              request-> request.header("header-0").orElseThrow(),
                                                                                                                                                      view);

        // Initial render
        scd.render(renderContext);
        assertHtmlFragmentsEqual("<div>\n" +
                                 " <span>header-0-value</span>\n" +
                                 "</div>",
                                 renderContext.html());
    }
}
