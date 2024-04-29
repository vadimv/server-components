package rsp.component;

import org.junit.jupiter.api.Test;
import rsp.dom.VirtualDomPath;
import rsp.page.QualifiedSessionId;
import rsp.server.Path;
import rsp.server.TestCollectingRemoteOut;
import rsp.server.http.HttpRequest;
import rsp.server.http.PageStateOrigin;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.html.HtmlDsl.*;
import static rsp.util.HtmlAssertions.assertHtmlFragmentsEqual;
import static rsp.util.TestUtils.containsType;

public class StoredStateComponentDefinitionTests {
    static final Map<ComponentCompositeKey, Integer> stateStore = new HashMap<>();
    static final ComponentView<Boolean> view = state -> newState ->
            div(
                    span(Boolean.toString(state)),
                    when(state, () ->
                         new StoredStateComponentDefinition<>(100,
                                                              s -> ns -> div(text("test-store-" + s)),
                                                              stateStore))
            );

    @Test
    void component_renders_initial_html_and_after_state_set_generates_dom_update_commands() {
        final QualifiedSessionId qualifiedSessionId = new QualifiedSessionId("test-device", "test-session");
        final URI uri = URI.create("http://localhost");
        final HttpRequest httpRequest = new HttpRequest(HttpRequest.HttpMethod.GET,
                                                        uri,
                                                        uri.toString(),
                                                        Path.ROOT);
        final PageStateOrigin pageStateOrigin = new PageStateOrigin(httpRequest);
        final TestCollectingRemoteOut remoteOut = new TestCollectingRemoteOut();
        final ComponentRenderContext renderContext = new ComponentRenderContext(qualifiedSessionId,
                                                                                VirtualDomPath.of("1"),
                                                                                pageStateOrigin,
                                                                                remoteOut,
                                                                                new Object());
        final StatefulComponentDefinition<Boolean> scd = new InitialStateComponentDefinition<>(true,
                                                                                               view);
        // Initial render
        scd.render(renderContext);

        final String html0 = renderContext.rootTag().toString();
        assertHtmlFragmentsEqual("<div>\n" +
                                 " <span>true</span>\n" +
                                 " <div>\n" +
                                 "  test-store-100\n" +
                                 " </div>\n" +
                                 "</div>",
                                 html0);


        assertEquals(0, renderContext.rootComponent().recursiveEvents().size());

        // Remove sub component
        renderContext.rootComponent().setState(false);

        assertEquals(1, remoteOut.commands.size());
        assertTrue(containsType(TestCollectingRemoteOut.ModifyDomOutMessage.class, remoteOut.commands));
        remoteOut.clear();
        assertEquals(0, renderContext.rootComponent().recursiveEvents().size());

        // Add it back
        renderContext.rootComponent().setState(true);
        assertEquals(1, remoteOut.commands.size());
    }
}
