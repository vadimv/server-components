package rsp.component;

import org.junit.jupiter.api.Test;
import rsp.dom.VirtualDomPath;
import rsp.page.QualifiedSessionId;
import rsp.server.TestCollectingRemoteOut;
import rsp.server.Path;
import rsp.server.http.HttpRequest;
import rsp.server.http.PageStateOrigin;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.html.HtmlDsl.div;
import static rsp.html.HtmlDsl.span;
import static rsp.util.TestUtils.containsType;

public class InitialStateComponentDefinitionTests {

    static final ComponentView<String> view = state -> newState ->
            div(
                    span(state)
            );

    @Test
    void component_renders_initial_html_and_after_state_set_generates_dom_update_command() {
        final QualifiedSessionId qualifiedSessionId = new QualifiedSessionId("test-device", "test-session");
        final URI uri = URI.create("http:/localhost");
        final HttpRequest httpRequest = new HttpRequest(HttpRequest.HttpMethod.GET,
                                                        uri,
                                                        uri.toString(),
                                                        Path.ROOT);
        final PageStateOrigin pageStateOrigin = new PageStateOrigin(httpRequest);
        final TestCollectingRemoteOut remoteOut = new TestCollectingRemoteOut();
        final ComponentRenderContext renderContext = new ComponentRenderContext(qualifiedSessionId,
                                                                                VirtualDomPath.of("0"),
                                                                                pageStateOrigin,
                                                                                remoteOut);
        final StatefulComponentDefinition scd = new InitialStateComponentDefinition("test", "state-0", view);
        scd.render(renderContext);
        final String html0 = renderContext.rootTag().toString();
        assertTrue(html0.contains("state-0"));
        assertTrue(html0.contains("<span>"));
        assertTrue(html0.contains("<div>" ));

        renderContext.rootComponent().set("state-1");
        assertTrue(remoteOut.commands.size() == 1);
        assertTrue(containsType(TestCollectingRemoteOut.ModifyDomOutMessage.class, remoteOut.commands));

    }

}
