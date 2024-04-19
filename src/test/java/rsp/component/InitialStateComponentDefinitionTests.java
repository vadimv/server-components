package rsp.component;

import org.junit.jupiter.api.Test;
import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.page.EventContext;
import rsp.page.QualifiedSessionId;
import rsp.server.Path;
import rsp.server.TestCollectingRemoteOut;
import rsp.server.http.HttpRequest;
import rsp.server.http.PageStateOrigin;
import rsp.util.json.JsonDataType;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static rsp.html.HtmlDsl.*;
import static rsp.util.HtmlAssertions.assertHtmlFragmentsEqual;

public class InitialStateComponentDefinitionTests {

    static final ComponentView<String> view = state -> newState ->
            div(
                    span(state),
                    new InitialStateComponentDefinition<>("test",
                                                         100,
                                                         s -> ns -> div(a(on("click", c -> ns.set(101)),
                                                                          text("test-link-" + s))))
            );

    @Test
    void component_renders_initial_html_and_after_state_update_generates_dom_change_commands() {
        final QualifiedSessionId qualifiedSessionId = new QualifiedSessionId("test-device", "test-session");
        final URI uri = URI.create("http://localhost");
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
        final StatefulComponentDefinition<String> scd = new InitialStateComponentDefinition<>("state-0",
                                                                                              view);
        // Initial render
        scd.render(renderContext);

        final String html0 = renderContext.rootTag().toString();
        assertEquals("state-0", renderContext.rootComponent().getState());

        assertHtmlFragmentsEqual("<div>\n" +
                                 " <span>state-0</span>\n" +
                                 " <div>\n" +
                                 "  <a>test-link-100</a>\n" +
                                 " </div>\n" +
                                 "</div>",
                                 html0);

        assertEquals(1, renderContext.rootComponent().recursiveEvents().size());
        assertEquals("click", renderContext.rootComponent().recursiveEvents().get(0).eventTarget.eventType);

        // Set state
        renderContext.rootComponent().set("state-1");

        assertEquals("state-1", renderContext.rootComponent().getState());

        assertEquals(1, remoteOut.commands.size());
        assertInstanceOf(TestCollectingRemoteOut.ModifyDomOutMessage.class, remoteOut.commands.get(0));
        assertTrue(((TestCollectingRemoteOut.ModifyDomOutMessage) remoteOut.commands.get(0)).domChange.size() > 0);
        remoteOut.clear();

        // Check for a registered event
        assertEquals(1, renderContext.rootComponent().recursiveEvents().size());
        assertEquals("click", renderContext.rootComponent().recursiveEvents().get(0).eventTarget.eventType);

        // Click
        final Event clickEvent = renderContext.rootComponent().recursiveEvents().get(0);
        final EventContext clickEventContext = new EventContext(clickEvent.eventTarget.elementPath,
                                                                js -> CompletableFuture.completedFuture(JsonDataType.Object.EMPTY),
                                                                ref -> null,
                                                                JsonDataType.Object.EMPTY,
                                                                (eventElementPath, customEvent) -> {},
                                                                ref -> {});
        clickEvent.eventHandler.accept(clickEventContext);

        assertEquals(1, remoteOut.commands.size());
        assertInstanceOf(TestCollectingRemoteOut.ModifyDomOutMessage.class, remoteOut.commands.get(0));
        assertTrue(remoteOut.commands.get(0).toString().contains("test-link-101"));
    }
}
