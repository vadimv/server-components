package rsp.component;

import org.junit.jupiter.api.Test;
import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.page.*;
import rsp.ref.TimerRef;
import rsp.server.TestCollectingRemoteOut;
import rsp.server.Path;
import rsp.server.http.HttpRequest;
import rsp.server.http.PageStateOrigin;
import rsp.util.json.JsonDataType;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.html.HtmlDsl.*;
import static rsp.util.TestUtils.containsType;

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
                                                                                VirtualDomPath.of("0"),
                                                                                pageStateOrigin,
                                                                                remoteOut);
        final StatefulComponentDefinition<String> scd = new InitialStateComponentDefinition<>("test",
                                                                                              "state-0",
                                                                                              view);
        // Initial render
        scd.render(renderContext);

        final String html0 = renderContext.rootTag().toString();
        assertTrue(html0.contains("state-0"));
        assertTrue(html0.contains("<span>"));
        assertTrue(html0.contains("<div>"));

        assertTrue(html0.contains("<a>"));
        assertTrue(html0.contains("test-link-100"));

        // Set state
        renderContext.rootComponent().set("state-1");
        assertEquals(1, remoteOut.commands.size());
        assertTrue(containsType(TestCollectingRemoteOut.ModifyDomOutMessage.class, remoteOut.commands));
        remoteOut.clear();
        assertEquals(1, renderContext.rootComponent().recursiveEvents().size());
        assertEquals("click", renderContext.rootComponent().recursiveEvents().get(0).eventTarget.eventType);

        // Click
        final Event clickEvent = renderContext.rootComponent().recursiveEvents().get(0);
        final EventContext clickEventContext = new EventContext(clickEvent.eventTarget.elementPath,
                js -> CompletableFuture.completedFuture(JsonDataType.Object.EMPTY),
                ref -> null,
                JsonDataType.Object.EMPTY,
                (eventElementPath, customEvent) -> {},
                new Schedule() {
                    @Override
                    public void scheduleAtFixedRate(Runnable command, TimerRef key, long initialDelay, long period, TimeUnit unit) {}

                    @Override
                    public void schedule(Runnable command, TimerRef key, long delay, TimeUnit unit) {}

                    @Override
                    public void cancel(TimerRef key) {}
                },
                ref -> {});
        clickEvent.eventHandler.accept(clickEventContext);

        assertEquals(1, remoteOut.commands.size());
        assertTrue(containsType(TestCollectingRemoteOut.ModifyDomOutMessage.class, remoteOut.commands));
        assertTrue(remoteOut.commands.get(0).toString().contains("test-link-101"));
    }
}
