package rsp.component;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import rsp.component.definitions.InitialStateComponent;
import rsp.component.definitions.Component;
import rsp.component.definitions.StoredStateComponent;
import rsp.dom.DefaultDomChangesContext;
import rsp.dom.DomEventEntry;
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
import static rsp.dsl.Html.*;
import static rsp.util.HtmlAssertions.assertHtmlFragmentsEqual;
import static rsp.util.TestUtils.findFirstListElementByType;
@Disabled
class StoredStateComponentTests {
    static final Map<ComponentCompositeKey, Integer> stateStore = new HashMap<>();
    static final ComponentView<Boolean> view = newState -> state ->
            div(
                    span(text("toggle"), on("click", ctx -> newState.setState(!state))),
                    when(state, () ->
                         new StoredStateComponent<>(100,
                                                              stateStore) {

                             @Override
                             public ComponentView<Integer> componentView() {
                                 return __ -> s -> div(text("test-store-" + s));
                             }
                         })
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
        final TreeBuilder renderContext = new TreeBuilder(qualifiedSessionId,
                                                                                TreePositionPath.of("1"),
                                                                                new ComponentContext(),
                                                                                commands);
        final Component<Boolean> scd = new InitialStateComponent<>(true,
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
        final DomEventEntry clickEvent = (DomEventEntry) renderContext.recursiveEvents().get(0);
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
        final DomEventEntry clickEvent2 = renderContext.recursiveEvents().get(0);
        clickEvent2.eventHandler.accept(clickEventContext);
        assertEquals(1, commands.list.size());
        final RemoteCommand.ModifyDom modifyDomOutMessage2 = findFirstListElementByType(RemoteCommand.ModifyDom.class, commands.list).orElseThrow();
        assertTrue(modifyDomOutMessage2.toString().contains("test-store-100"));
    }
}
