package rsp.component;

import org.junit.jupiter.api.Test;
import rsp.component.definitions.PathStateComponentDefinition;
import rsp.dom.Event;
import rsp.dom.TreePositionPath;
import rsp.page.EventContext;
import rsp.page.QualifiedSessionId;
import rsp.page.events.RemoteCommand;
import rsp.server.Path;
import rsp.server.TestSessonEventsConsumer;
import rsp.server.http.HttpRequest;
import rsp.util.json.JsonDataType;

import java.net.URI;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static rsp.html.HtmlDsl.*;
import static rsp.util.HtmlAssertions.assertHtmlFragmentsEqual;
import static rsp.util.TestUtils.findFirstListElementByType;

class PathStateComponentDefinitionTests {

    static final ComponentView<String> view = newState -> state ->
            div(
                    span(text(state), on("click", ctx -> newState.setState("state-1")))
            );

    @Test
    void component_renders_initial_html_and_after_state_set_generates_dom_update_commands_and_update_path() {
        final QualifiedSessionId qualifiedSessionId = new QualifiedSessionId("test-device", "test-session");
        final URI uri = URI.create("http://localhost/state-0");
        final HttpRequest httpRequest = new HttpRequest(HttpRequest.HttpMethod.GET,
                                                        uri,
                                                        uri.toString(),
                                                        Path.of(uri.getPath()));
        final TestSessonEventsConsumer commands = new TestSessonEventsConsumer();
        final ComponentRenderContext renderContext = new ComponentRenderContext(qualifiedSessionId,
                                                                                TreePositionPath.of("1"),
                                                                                new HashMap<>(),
                                                                                commands);
        final PathStateComponentDefinition<String> scd = new PathStateComponentDefinition<>(httpRequest.relativeUrl(),
                                                                                       path -> path.get(0),
                                                                                           (state, path) -> Path.of("/" + state),
                                                                                            view);
        // Initial render
        scd.render(renderContext);

        assertHtmlFragmentsEqual("<div>\n" +
                                 " <span>state-0</span>\n" +
                                 "</div>",
                                 renderContext.html());

        assertTrue(renderContext.recursiveEvents().stream().map(e -> e.eventTarget.eventType()).anyMatch(s -> s.equals("click")));
        assertTrue(renderContext.recursiveEvents().stream().map(e -> e.eventTarget.eventType()).anyMatch(s -> s.equals("popstate")));

        // Click
        final Event clickEvent = renderContext.recursiveEvents().stream().filter(e -> e.eventTarget.eventType().equals("click")).findFirst().get();
        final EventContext clickEventContext1 = new EventContext(clickEvent.eventTarget.elementPath(),
                                                                 js -> CompletableFuture.completedFuture(JsonDataType.Object.EMPTY),
                                                                 ref -> null,
                                                                 JsonDataType.Object.EMPTY,
                                                                 (eventElementPath, customEvent) -> {},
                                                                 ref -> {});
        clickEvent.eventHandler.accept(clickEventContext1);

        assertEquals(2, commands.list.size());
        final RemoteCommand.ModifyDom modifyDomOutMessage = findFirstListElementByType(RemoteCommand.ModifyDom.class, commands.list).orElseThrow();
        assertTrue(modifyDomOutMessage.toString().contains("state-1"));

        final RemoteCommand.PushHistory pushHistoryMessage = findFirstListElementByType(RemoteCommand.PushHistory.class, commands.list).orElseThrow();
        assertTrue(pushHistoryMessage.toString().contains("state-1"));

        commands.list.clear();

        // History backward
        final Event popstateEvent = renderContext.recursiveEvents().stream().filter(e -> "popstate".equals(e.eventTarget.eventType())).findFirst().orElseThrow();
        final EventContext popstateEventContext = new EventContext(popstateEvent.eventTarget.elementPath(),
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
        popstateEvent.eventHandler.accept(popstateEventContext);

        assertEquals(2, commands.list.size());

        final RemoteCommand.ModifyDom modifyDomOutMessage2 = findFirstListElementByType(RemoteCommand.ModifyDom.class, commands.list).orElseThrow();
        assertTrue(modifyDomOutMessage2.toString().contains("state-0"));

        final RemoteCommand.PushHistory pushHistoryMessage2 = findFirstListElementByType(RemoteCommand.PushHistory.class, commands.list).orElseThrow();
        assertTrue(pushHistoryMessage2.toString().contains("state-0"));
    }
}
