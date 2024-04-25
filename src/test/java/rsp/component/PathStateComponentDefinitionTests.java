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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.html.HtmlDsl.div;
import static rsp.html.HtmlDsl.span;
import static rsp.util.HtmlAssertions.assertHtmlFragmentsEqual;
import static rsp.util.TestUtils.containsType;

public class PathStateComponentDefinitionTests {

    static final ComponentView<String> view = state -> newState ->
            div(
                    span(state)
            );

    @Test
    void component_renders_initial_html_and_after_state_set_generates_dom_update_commands_and_update_path() {
        final QualifiedSessionId qualifiedSessionId = new QualifiedSessionId("test-device", "test-session");
        final URI uri = URI.create("http://localhost/state-0");
        final HttpRequest httpRequest = new HttpRequest(HttpRequest.HttpMethod.GET,
                                                        uri,
                                                        uri.toString(),
                                                        Path.of(uri.getPath()));
        final PageStateOrigin pageStateOrigin = new PageStateOrigin(httpRequest);
        final TestCollectingRemoteOut remoteOut = new TestCollectingRemoteOut();
        final ComponentRenderContext renderContext = new ComponentRenderContext(qualifiedSessionId,
                                                                                VirtualDomPath.of("0"),
                                                                                pageStateOrigin,
                                                                                remoteOut);
        final PathStateComponentDefinition<String> scd = new PathStateComponentDefinition<>(path -> CompletableFuture.completedFuture(path.get(0)),
                                                                                           (state, path) -> Path.of("/" + state),
                                                                                            view);
        // Initial render
        scd.render(renderContext);

        final String html0 = renderContext.rootTag().toString();
        assertHtmlFragmentsEqual("<div>\n" +
                                 " <span>state-0</span>\n" +
                                 "</div>",
                                 html0);

        // Set state
        renderContext.rootComponent().setState("state-1");
        
        assertTrue(containsType(TestCollectingRemoteOut.ModifyDomOutMessage.class, remoteOut.commands));

        remoteOut.clear();
        assertEquals(1, renderContext.rootComponent().recursiveEvents().size());
        assertEquals("popstate", renderContext.rootComponent().recursiveEvents().get(0).eventTarget.eventType);

        // History backward
        final Event popstateEvent = renderContext.rootComponent().recursiveEvents().get(0);
        final EventContext clickEventContext = new EventContext(popstateEvent.eventTarget.elementPath,
                                                                js -> CompletableFuture.completedFuture(JsonDataType.Object.EMPTY),
                                                                ref -> null,
                                                                JsonDataType.Object.EMPTY.put("path",
                                                                                              new JsonDataType.String("state-0"))
                                                                                         .put("query",
                                                                                              new JsonDataType.String(""))
                                                                                         .put("fragment",
                                                                                              new JsonDataType.String("")),
                                                                (eventElementPath, customEvent) -> {},
                                                                ref -> {});
        popstateEvent.eventHandler.accept(clickEventContext);

        assertEquals(2, remoteOut.commands.size());
        assertTrue(containsType(TestCollectingRemoteOut.ModifyDomOutMessage.class, remoteOut.commands));
        assertTrue(remoteOut.commands.get(0).toString().contains("state-0"));

    }
}
