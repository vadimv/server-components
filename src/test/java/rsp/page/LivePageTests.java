package rsp.page;

import org.junit.jupiter.api.Test;
import rsp.component.Component;
import rsp.component.ComponentDsl;
import rsp.component.ComponentView;
import rsp.component.PathStatefulComponentDefinition;
import rsp.dom.DomTreeRenderContext;
import rsp.dom.VirtualDomPath;
import rsp.server.Path;
import rsp.server.RemoteOut;
import rsp.server.http.HttpRequest;
import rsp.server.http.HttpStateOrigin;
import rsp.server.http.HttpStateOriginLookup;
import rsp.server.http.RelativeUrl;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
    public void should_generate_html_listen_event_and_update_commands_for_new_state() throws IOException {
        final TestCollectingRemoteOut remoteOut = new TestCollectingRemoteOut();
        final State initialState = new State(10);

        final AtomicReference<RemoteOut> remoteOutReference = new AtomicReference<>();
        remoteOutReference.set(remoteOut);

        final HttpStateOriginLookup lookup = new HttpStateOriginLookup(new HttpStateOrigin(HttpRequest.DUMMY,
                                                                       RelativeUrl.of(HttpRequest.DUMMY)));

        final RenderContext renderContext = new DomTreeRenderContext(VirtualDomPath.of("1"),
                                                                        Path.of(""),
                                                                        lookup,
                                                                        remoteOutReference);

        final RenderContext enrichedDomTreeContext = UpgradingRenderContext.create(renderContext,
                                                                                    QID.sessionId,
                                                                                    "/",
                                                                                    DefaultConnectionLostWidget.HTML,
                                                                                    1000);
        final PathStatefulComponentDefinition<State> componentDefinition = ComponentDsl.component(initialState, view);
        componentDefinition.render(enrichedDomTreeContext);
        assertFalse(enrichedDomTreeContext.toString().isBlank());

        final Component<String, State> rootComponent = enrichedDomTreeContext.rootComponent();
        final LivePageSession livePage = new LivePageSession(QID,
                                                                lookup,
                                                                new Schedules(Executors.newScheduledThreadPool(1)),
                                                                rootComponent,
                                                                remoteOut);
        livePage.init();
        assertInstanceOf(ListenEventOutMessage.class, remoteOut.commands.get(0));

        rootComponent.set(new State(100));
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