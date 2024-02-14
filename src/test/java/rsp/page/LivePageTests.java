package rsp.page;

import org.junit.jupiter.api.Test;
import rsp.component.Component;
import rsp.dom.*;
import rsp.server.Path;
import rsp.server.RemoteOut;
import rsp.server.http.*;
import rsp.component.ComponentView;
import rsp.component.NewState;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rsp.html.HtmlDsl.span;
import static rsp.page.TestCollectingRemoteOut.*;

public class LivePageTests {

    private static final QualifiedSessionId QID = new QualifiedSessionId("1", "1");

    @Test
    public void should_generate_update_commands_for_new_state() {
        final TestCollectingRemoteOut out = new TestCollectingRemoteOut();
        final NewState<State> liveComponent = createComponent(out);
        liveComponent.set(new State(100));
        assertEquals(List.of(new ListenEventOutMessage("popstate", true, VirtualDomPath.WINDOW, Event.NO_MODIFIER),
                                    new ModifyDomOutMessage(List.of(new DefaultDomChangesContext.Create(VirtualDomPath.of("1"), XmlNs.html, "span"),
                                                                    new DefaultDomChangesContext.CreateText(VirtualDomPath.of("1"), VirtualDomPath.of("1_1"), "100"))),
                                    new PushHistoryMessage("basePath/100")),
                out.commands);
    }

    private Component<String, State> createComponent(RemoteOut remoteOut) {
        final State initialState = new State(0);
        final ComponentView<State> view = state -> newState -> span(state.toString());

        final AtomicReference<RemoteOut> remoteOutReference = new AtomicReference<>();
        remoteOutReference.set(remoteOut);
        final HttpStateOriginLookup lookup = new HttpStateOriginLookup(new HttpStateOrigin(HttpRequest.DUMMY, RelativeUrl.of(HttpRequest.DUMMY)));
        final RenderContext renderContext = new DomTreeRenderContext(VirtualDomPath.of("1"),
                                                                     Path.of(""),
                                                                     new HttpStateOriginLookup(new HttpStateOrigin(HttpRequest.DUMMY, RelativeUrl.of(HttpRequest.DUMMY))),
                                                                     remoteOutReference);
        final Component<String, State> component = new Component<>(new Object(),
                                                                   Path.of("basePath"),
                                                                   new HttpStateOriginProvider<>(lookup, String.class),
                                                                   t -> CompletableFuture.completedFuture(initialState),
                                                                   (s, p) -> Path.of(String.valueOf(s.value)),
                                                                   view,
                                                                   renderContext,
                                                                   remoteOutReference);
        final LivePageSession livePage = new LivePageSession(QID,
                                                             lookup,
                                                             new Schedules(Executors.newScheduledThreadPool(1)),
                                                             component,
                                                             remoteOut);
        livePage.init();

        return component;
    }

    private static class State {
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