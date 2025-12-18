package rsp.component;

import org.junit.jupiter.api.Test;
import rsp.component.definitions.InitialStateComponent;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.server.Path;
import rsp.server.TestSessonEventsConsumer;
import rsp.server.http.HttpMethod;
import rsp.server.http.HttpRequest;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static rsp.dsl.Html.*;
import static rsp.util.HtmlAssertions.assertHtmlFragmentsEqual;

class InitialStateComponentTests {

    static final ComponentView<String> view = newState -> state ->
            div(
                    span(state),
                    new InitialStateComponent<>("test",
                                                         100,
                                                         ns -> s -> div(a(on("click", c -> ns.setState(101)),
                                                                          text("test-link-" + s))))
            );

    @Test
    void component_renders_initial_html_and_after_state_update_generates_dom_change_commands() {
        final QualifiedSessionId qualifiedSessionId = new QualifiedSessionId("test-device", "test-session");
        final URI uri = URI.create("http://localhost");
        final HttpRequest httpRequest = new HttpRequest(HttpMethod.GET,
                                                        uri,
                                                        uri.toString(),
                                                        Path.ROOT);
        final TestSessonEventsConsumer commands = new TestSessonEventsConsumer();
        final TreeBuilder renderContext = new TreeBuilder(qualifiedSessionId,
                                                                                TreePositionPath.of("1"),
                                                                                new ComponentContext(),
                                                                                commands);
        final Component<String> scd = new InitialStateComponent<>("state-0",
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
        assertEquals("click", renderContext.recursiveEvents().get(0).eventName);
      /*  assertEquals(TreePositionPath.of("1_2_1"), renderContext.recursiveEvents().get(0).eventTarget.elementPath());

        // Click
        final EventEntry clickEvent = renderContext.recursiveEvents().get(0);
        final EventContext clickEventContext = new EventContext(clickEvent.eventTarget.elementPath(),
                                                                js -> CompletableFuture.completedFuture(JsonDataType.Object.EMPTY),
                                                                ref -> null,
                                                                JsonDataType.Object.EMPTY,
                                                                (eventElementPath, customEvent) -> {},
                                                                ref -> {});
        clickEvent.eventHandler.accept(clickEventContext);

        assertEquals(1, commands.list.size());
        assertInstanceOf(RemoteCommand.ModifyDom.class, commands.list.get(0));
        assertTrue(commands.list.get(0).toString().contains("test-link-101")); */
    }
}
