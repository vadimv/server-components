package rsp.page;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import rsp.component.*;
import rsp.dom.TreePositionPath;
import rsp.server.Path;
import rsp.server.TestCollectingRemoteOut;
import rsp.server.http.*;
import rsp.util.json.JsonDataType;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static rsp.html.HtmlDsl.*;
import static rsp.page.PageRendering.DOCUMENT_DOM_PATH;
import static rsp.util.TestUtils.findFirstListElementByType;

class LivePageTests {

    static final QualifiedSessionId QID = new QualifiedSessionId("1", "1");

    static final ComponentView<State> view = newState -> state -> html(
            body(
                    span(text(state.toString()), on("custom-event-0", eventContext -> {
                        eventContext.evalJs("1000+1").thenAccept(value -> newState.setState(new State(value.asJsonNumber().asLong())));
                    }))
            )
    );

    @Test
    void should_generate_html_listen_event_and_update_commands_for_new_state() {
        final TestCollectingRemoteOut remoteOut = new TestCollectingRemoteOut();
        final State initialState = new State(10);

        final URI uri = URI.create("http://localhost");
        final HttpRequest httpRequest = new HttpRequest(HttpRequest.HttpMethod.GET,
                                                        uri,
                                                        uri.toString(),
                                                        Path.ROOT);

        final PageConfigScript pageConfigScript = new PageConfigScript(QID.sessionId(),
                                                                       "/",
                                                                       DefaultConnectionLostWidget.HTML,
                                                                       1000);
        final TemporaryBufferedPageCommands commandsBuffer = new TemporaryBufferedPageCommands();
        final Object sessionLock = new Object();
        final PageRenderContext domTreeContext = new PageRenderContext(QID,
                                                                       pageConfigScript.toString(),
                                                                       DOCUMENT_DOM_PATH,
                                                                       commandsBuffer,
                                                                       sessionLock);

        final StatefulComponentDefinition<State> componentDefinition = new PathStateComponentDefinition<>(httpRequest.relativeUrl(),
                                                                                                     p -> initialState,
                                                                                                          (s, p) -> p,
                                                                                                          view);

        componentDefinition.render(domTreeContext);

        final Document pageHtml = org.jsoup.Jsoup.parse(domTreeContext.html());
        assertEquals(2, pageHtml.head().children().size());
        assertEquals("script", pageHtml.head().children().get(0).nodeName());
        assertEquals("script", pageHtml.head().children().get(1).nodeName());

        final LivePageSession livePage = new LivePageSession();
        assertEquals(0, remoteOut.commands.size());

        livePage.start();
        //commandsBuffer.redirectMessagesOut(remoteOut);

        assertEquals(1, remoteOut.commands.size());
        remoteOut.commands.clear();

        //livePage.handleDomEvent(1, TreePositionPath.of("1_2_1"), "custom-event-0", new JsonDataType.Object().put("", new JsonDataType.Number(101)));
        assertEquals(1, remoteOut.commands.size());
        assertInstanceOf(TestCollectingRemoteOut.EvalJsMessage.class, remoteOut.commands.get(0));
        remoteOut.commands.clear();

        //livePage.handleEvalJsResponse(1, new JsonDataType.Number(1001));
        final var modifyDomOutMessage = findFirstListElementByType(TestCollectingRemoteOut.ModifyDomOutMessage.class, remoteOut.commands);
        assertTrue(modifyDomOutMessage.isPresent() && modifyDomOutMessage.get().domChange.get(0).toString().contains("1001"));
    }

    static final class State {
        public final long value;

        private State(final long value) {
        this.value = value;
        }

        @Override
        public String toString() {
        return Long.toString(value);
        }
    }
}