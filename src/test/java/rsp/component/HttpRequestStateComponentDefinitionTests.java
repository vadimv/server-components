package rsp.component;

import org.junit.jupiter.api.Test;
import rsp.dom.NodeList;
import rsp.dom.VirtualDomPath;
import rsp.page.QualifiedSessionId;
import rsp.server.Path;
import rsp.server.TestCollectingRemoteOut;
import rsp.server.http.HttpRequest;
import rsp.server.http.PageStateOrigin;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static rsp.html.HtmlDsl.div;
import static rsp.html.HtmlDsl.span;
import static rsp.util.HtmlAssertions.assertHtmlFragmentsEqual;

public class HttpRequestStateComponentDefinitionTests {

    static final ComponentView<String> view = state -> newState ->
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
                                                        name -> Optional.empty(),
                                                        name -> name.equals("header-0") ? Optional.of("header-0-value") : Optional.empty());
        final PageStateOrigin pageStateOrigin = new PageStateOrigin(httpRequest);
        final TestCollectingRemoteOut remoteOut = new TestCollectingRemoteOut();
        final ComponentRenderContext renderContext = new ComponentRenderContext(qualifiedSessionId,
                                                                                VirtualDomPath.of("1"),
                                                                                pageStateOrigin,
                                                                                remoteOut,
                                                                                new Object());
        final HttpRequestStateComponentDefinition<String> scd = new HttpRequestStateComponentDefinition<>(
                request-> CompletableFuture.completedFuture(request.header("header-0").orElseThrow()),
                                                            view);

        // Initial render
        scd.render(renderContext);

        final StringBuilder sb = new StringBuilder();
        renderContext.rootNodes().appendString(sb);
        final String html0 = sb.toString();
        assertHtmlFragmentsEqual("<div>\n" +
                                 " <span>header-0-value</span>\n" +
                                 "</div>",
                                 html0);
    }
}
