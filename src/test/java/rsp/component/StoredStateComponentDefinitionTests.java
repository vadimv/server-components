package rsp.component;

import org.junit.jupiter.api.Test;
import rsp.dom.DefaultDomChangesContext;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static rsp.html.HtmlDsl.*;
import static rsp.util.HtmlAssertions.assertHtmlFragmentsEqual;
import static rsp.util.TestUtils.findFirstListElementByType;

class StoredStateComponentDefinitionTests {
    static final Map<ComponentCompositeKey, Integer> stateStore = new HashMap<>();
    static final ComponentView<Boolean> view = newState -> state ->
            div(
                    span(text("toggle"), on("click", ctx -> newState.setState(!state))),
                    when(state, () ->
                         new StoredStateComponentDefinition<>(100,
                                                              __ -> s -> div(text("test-store-" + s)),
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
        final TestCollectingRemoteOut remoteOut = new TestCollectingRemoteOut();
        final ComponentRenderContext renderContext = new ComponentRenderContext(qualifiedSessionId,
                                                                                TreePositionPath.of("1"),
                                                                                null);
        final StatefulComponentDefinition<Boolean> scd = new InitialStateComponentDefinition<>(true,
                                                                                               view);
        // Initial render
        scd.render(renderContext);

        assertHtmlFragmentsEqual("<div>\n" +
                                 " <span>toggle</span>\n" +
                                 " <div>\n" +
                                 "  test-store-100\n" +
                                 " </div>\n" +
                                 "</div>",
                                renderContext.html());

        assertEquals(1, renderContext.recursiveEvents().size());

        // Remove sub component
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
        final TestCollectingRemoteOut.ModifyDomOutMessage modifyDomOutMessage = findFirstListElementByType(TestCollectingRemoteOut.ModifyDomOutMessage.class, remoteOut.commands).orElseThrow();
        assertEquals(1, modifyDomOutMessage.domChange.size());
        assertInstanceOf(DefaultDomChangesContext.Remove.class, modifyDomOutMessage.domChange.get(0));

        remoteOut.clear();

        // Add the hidden stateful component back
        final Event clickEvent2 = renderContext.recursiveEvents().get(0);
        clickEvent2.eventHandler.accept(clickEventContext);
        assertEquals(1, remoteOut.commands.size());
        final TestCollectingRemoteOut.ModifyDomOutMessage modifyDomOutMessage2 = findFirstListElementByType(TestCollectingRemoteOut.ModifyDomOutMessage.class, remoteOut.commands).orElseThrow();
        assertTrue(modifyDomOutMessage2.toString().contains("test-store-100"));
    }
}
