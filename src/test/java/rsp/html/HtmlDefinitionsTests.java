package rsp.html;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.component.ComponentRenderContext;
import rsp.component.definitions.InitialStateComponent;
import rsp.component.View;
import rsp.page.PageRendering;
import rsp.page.QualifiedSessionId;
import rsp.server.Path;
import rsp.server.TestCollectingRemoteOut;
import rsp.server.http.HttpRequest;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.html.HtmlDsl.*;

public class HtmlDefinitionsTests {

    final View<String> view = __ -> html(head(title("test-title"),
                                              script(attr("type", "text/javascript"))),
                                        body(div(span("text-0"),
                                                 br(),
                                                 a("link", "link"))));

    final String expectedHtml = """
                <!DOCTYPE html>
                <html>
                    <head>
                      <title>test-title</title>
                      <script type="text/javascript"></script>
                    </head>
                    <body>
                        <div>
                          <span>text-0</span>
                          <br>
                          <a href="link">link</a>
                        </div>
                    </body>
                </html>
                """;
    @Test
    void properly_defines_html_markup() {
        final Document html = org.jsoup.Jsoup.parse(htmlOf(view, ""));
        System.out.println(html);

        assertTrue(org.jsoup.Jsoup.parse(expectedHtml).hasSameValue(html));
    }

    private static <S> String htmlOf(final View<S> view, final S initialState) {
        final var component = new InitialStateComponent<>(initialState, view);
        final ComponentRenderContext rc = createRenderContext();
        component.render(rc);
        return rc.html();
    }

    private static ComponentRenderContext createRenderContext() {
        final QualifiedSessionId qualifiedSessionId = new QualifiedSessionId("0", "0");
        final URI uri = URI.create("http://localhost");
        final HttpRequest httpRequest = new HttpRequest(HttpRequest.HttpMethod.GET,
                                                        uri,
                                                        uri.toString(),
                                                        Path.ROOT);
        final ComponentRenderContext rc = new ComponentRenderContext(qualifiedSessionId,
                                                                     PageRendering.DOCUMENT_DOM_PATH,
                                                                     new ComponentContext(),
                                                                     __ -> new TestCollectingRemoteOut());
        return rc;

    }
}
