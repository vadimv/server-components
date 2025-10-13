package rsp.component;

import org.junit.jupiter.api.Test;
import rsp.component.definitions.InitialStateComponentDefinition;
import rsp.component.definitions.StatefulComponentDefinition;
import rsp.component.definitions.StoredStateComponentDefinition;
import rsp.dom.DefaultDomChangesContext;
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
        final TestSessonEventsConsumer commands = new TestSessonEventsConsumer();
        final ComponentRenderContext renderContext = new ComponentRenderContext(qualifiedSessionId,
                                                                                TreePositionPath.of("1"),
                                                                                new HashMap<>(),
                                                                                commands);
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

        assertEquals(1, commands.list.size());
        final var modifyDomOutMessage = findFirstListElementByType(RemoteCommand.ModifyDom.class, commands.list).orElseThrow();
        assertEquals(1, modifyDomOutMessage.domChanges().size());
        assertInstanceOf(DefaultDomChangesContext.Remove.class, modifyDomOutMessage.domChanges().getFirst());

        commands.list.clear();

        // Add the hidden stateful component back
        final Event clickEvent2 = renderContext.recursiveEvents().get(0);
        clickEvent2.eventHandler.accept(clickEventContext);
        assertEquals(1, commands.list.size());
        final RemoteCommand.ModifyDom modifyDomOutMessage2 = findFirstListElementByType(RemoteCommand.ModifyDom.class, commands.list).orElseThrow();
        assertTrue(modifyDomOutMessage2.toString().contains("test-store-100"));
    }
}
