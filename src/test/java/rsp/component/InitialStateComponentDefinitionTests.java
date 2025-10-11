package rsp.component;

import org.junit.jupiter.api.Test;
import rsp.dom.Event;
import rsp.dom.TreePositionPath;
import rsp.page.EventContext;
import rsp.page.QualifiedSessionId;
import rsp.server.Path;
import rsp.server.TestCollectingRemoteOut;
import rsp.server.http.HttpRequest;
import rsp.util.json.JsonDataType;

import java.net.URI;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static rsp.html.HtmlDsl.*;
import static rsp.util.HtmlAssertions.assertHtmlFragmentsEqual;

class InitialStateComponentDefinitionTests {

    static final ComponentView<String> view = newState -> state ->
            div(
                    span(state),
                    new InitialStateComponentDefinition<>("test",
                                                         100,
                                                         ns -> s -> div(a(on("click", c -> ns.setState(101)),
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
        final TestCollectingRemoteOut remoteOut = new TestCollectingRemoteOut();
        final ComponentRenderContext renderContext = new ComponentRenderContext(qualifiedSessionId,
                                                                                TreePositionPath.of("1"),
                                                                                new HashMap<>(),
                                                                                null);
        final StatefulComponentDefinition<String> scd = new InitialStateComponentDefinition<>("state-0",
                                                                                              view);
        // Initial render
        scd.render(renderContext);

        assertHtmlFragmentsEqual("<div>\n" +
                                 " <span>state-0</span>\n" +
                                 " <div>\n" +
                                 "  <a>test-link-100</a>\n" +
                                 " </div>\n" +
                                 "</div>",
                                 renderContext.html());

        assertEquals(1, renderContext.recursiveEvents().size());
        assertEquals("click", renderContext.recursiveEvents().get(0).eventTarget.eventType());
        assertEquals(TreePositionPath.of("1_2_1"), renderContext.recursiveEvents().get(0).eventTarget.elementPath());

        // Click
        final Event clickEvent = renderContext.recursiveEvents().get(0);
        final EventContext clickEventContext = new EventContext(clickEvent.eventTarget.elementPath(),
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
