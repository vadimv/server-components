package rsp.page;

import org.junit.jupiter.api.Test;
import rsp.component.Component;
import rsp.component.ComponentDsl;
import rsp.component.ComponentView;
import rsp.component.PathStatefulComponentDefinition;
import rsp.dom.VirtualDomPath;
import rsp.server.Path;
import rsp.server.http.HttpRequest;
import rsp.server.http.HttpStateOrigin;
import rsp.server.http.HttpStateOriginLookup;

import java.net.URI;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static rsp.html.HtmlDsl.*;
import static rsp.page.TestCollectingRemoteOut.ListenEventOutMessage;
import static rsp.page.TestCollectingRemoteOut.ModifyDomOutMessage;

public class LivePageTests {

    static final QualifiedSessionId QID = new QualifiedSessionId("1", "1");

    static final ComponentView<State> view = state -> newState -> html(
            body(
                    span(state.toString())
            )
    );

    @Test
    public void should_generate_html_listen_event_and_update_commands_for_new_state() {
        final TestCollectingRemoteOut remoteOut = new TestCollectingRemoteOut();
        final State initialState = new State(10);

        final URI uri = URI.create("http:/localhost");
        final HttpRequest httpRequest = new HttpRequest(HttpRequest.HttpMethod.GET,
                                                        uri,
                                                        uri.toString(),
                                                        Path.EMPTY_ABSOLUTE);

        final HttpStateOriginLookup lookup = new HttpStateOriginLookup(new HttpStateOrigin(httpRequest));


        final PageConfigScript pageConfigScript = new PageConfigScript(QID.sessionId,
                                                                        "/",
                                                                        DefaultConnectionLostWidget.HTML,
                                                                        1000);
        final PageRenderContext domTreeContext = new PageRenderContext(pageConfigScript.toString(),
                                                                        VirtualDomPath.DOCUMENT,
                                                                        Path.of(""),
                                                                        lookup,
                                                                        new TemporaryBufferedPageCommands());

        final PathStatefulComponentDefinition<State> componentDefinition = ComponentDsl.component(initialState, view);
        componentDefinition.render(domTreeContext);
        assertFalse(domTreeContext.toString().isBlank());

        final Component<String, State> rootComponent = domTreeContext.rootComponent();
        assertNotNull(rootComponent);

        final LivePageSession livePage = new LivePageSession(QID,
                                                                lookup,
                                                                new Schedules(Executors.newScheduledThreadPool(1)),
                                                                rootComponent,
                                                                remoteOut);
        livePage.init();
        assertInstanceOf(ListenEventOutMessage.class, remoteOut.commands.get(0));

        rootComponent.set(new State(100));
        rootComponent.redirectMessagesOut(remoteOut);
        assertInstanceOf(ModifyDomOutMessage.class, remoteOut.commands.get(1));
    }

    static final class State {
        public final int value;

        private State(final int value) {
        this.value = value;
        }

        @Override
        public String toString() {
        return Integer.toString(value);
        }
    }
}