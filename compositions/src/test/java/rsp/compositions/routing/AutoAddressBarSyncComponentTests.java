package rsp.compositions.routing;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.*;
import rsp.component.definitions.ContextStateComponent;
import rsp.dom.DomEventEntry;
import rsp.page.EventContext;
import rsp.page.events.Command;
import rsp.page.events.RemoteCommand;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rsp.compositions.routing.AutoAddressBarSyncComponent.PathUpdateMode.RE_RENDER_SUBTREE;
import static rsp.compositions.routing.AutoAddressBarSyncComponent.PathUpdateMode.UPDATE_PATH_ONLY;

/**
 * Tests for {@link AutoAddressBarSyncComponent} encoding the URL-state
 * invariants described in the architecture analysis:
 * <ul>
 *   <li><b>Invariant 1</b>: every {@code SET_PATH} event updates the
 *       component's {@link RelativeUrl} state — the URL bar shown to the user
 *       and the state populated into context must always agree. This holds
 *       regardless of the {@code PathUpdateMode}.</li>
 *   <li>Browser history is pushed on every URL change.</li>
 *   <li>Subsequent query-parameter updates use the latest state as their
 *       base, so chained navigations compose correctly.</li>
 * </ul>
 */
class AutoAddressBarSyncComponentTests {

    @Nested
    class SetPathUpdatesState {

        @Test
        void re_render_subtree_updates_state_and_pushes_history() {
            Harness h = harnessOn("/posts?p=2");
            RelativeUrl target = url("/comments");

            h.subscriber.emitComponentEvent(
                AutoAddressBarSyncComponent.SET_PATH.name(),
                new AutoAddressBarSyncComponent.PathUpdate(target, RE_RENDER_SUBTREE));

            assertEquals("/comments", h.stateUpdate.state().toString());
            assertEquals(List.of(new RemoteCommand.PushHistory("/comments")),
                         h.commandsEnqueue.commands());
        }

        @Test
        void update_path_only_also_updates_state_and_pushes_history() {
            // Invariant 1 regression: prior to the fix, UPDATE_PATH_ONLY pushed
            // history but skipped the state update, leaving subsequent renders
            // observing a stale URL via subComponentsContext.
            Harness h = harnessOn("/posts?p=2");
            RelativeUrl target = url("/comments");

            h.subscriber.emitComponentEvent(
                AutoAddressBarSyncComponent.SET_PATH.name(),
                new AutoAddressBarSyncComponent.PathUpdate(target, UPDATE_PATH_ONLY));

            assertEquals("/comments", h.stateUpdate.state().toString());
            assertEquals(List.of(new RemoteCommand.PushHistory("/comments")),
                         h.commandsEnqueue.commands());
        }

        @Test
        void set_path_with_query_and_fragment_updates_state_completely() {
            Harness h = harnessOn("/posts?p=2");
            RelativeUrl target = new RelativeUrl(
                Path.of("/comments"), Query.of("p=5"), new Fragment("anchor"));

            h.subscriber.emitComponentEvent(
                AutoAddressBarSyncComponent.SET_PATH.name(),
                new AutoAddressBarSyncComponent.PathUpdate(target, UPDATE_PATH_ONLY));

            assertEquals("/comments?p=5#anchor", h.stateUpdate.state().toString());
        }
    }

    @Nested
    class QueryParamUpdatesUseLatestState {

        @Test
        void chained_query_update_after_set_path_uses_new_path_as_base() {
            Harness h = harnessOn("/posts?p=2");

            h.subscriber.emitComponentEvent(
                AutoAddressBarSyncComponent.SET_PATH.name(),
                new AutoAddressBarSyncComponent.PathUpdate(
                    url("/comments"), UPDATE_PATH_ONLY));
            h.subscriber.emitComponentEvent(
                "stateUpdated.p",
                new ContextStateComponent.ContextValue.StringValue("3"));

            assertEquals("/comments?p=3", h.stateUpdate.state().toString(),
                         "query update must compose on top of the new path, not the old one");
            assertEquals(
                List.of(
                    new RemoteCommand.PushHistory("/comments"),
                    new RemoteCommand.PushHistory("/comments?p=3")),
                h.commandsEnqueue.commands());
        }

        @Test
        void standalone_query_update_uses_current_state() {
            Harness h = harnessOn("/posts?p=2");

            h.subscriber.emitComponentEvent(
                "stateUpdated.p",
                new ContextStateComponent.ContextValue.StringValue("4"));

            assertEquals("/posts?p=4", h.stateUpdate.state().toString());
        }
    }

    // --- Test harness ------------------------------------------------------

    private Harness harnessOn(String urlString) {
        RelativeUrl initial = parse(urlString);
        TestAutoAddressBarSyncComponent component = new TestAutoAddressBarSyncComponent(initial);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        RecordingCommandsEnqueue commandsEnqueue = new RecordingCommandsEnqueue();
        RecordingStateUpdate stateUpdate = new RecordingStateUpdate(initial);
        component.onAfterRendered(initial, subscriber, commandsEnqueue, stateUpdate);
        return new Harness(subscriber, commandsEnqueue, stateUpdate);
    }

    private record Harness(RecordingSubscriber subscriber,
                           RecordingCommandsEnqueue commandsEnqueue,
                           RecordingStateUpdate stateUpdate) {}

    private static RelativeUrl url(String path) {
        return new RelativeUrl(Path.of(path), Query.EMPTY, Fragment.EMPTY);
    }

    private static RelativeUrl parse(String urlString) {
        int q = urlString.indexOf('?');
        int h = urlString.indexOf('#');
        String path = q >= 0 ? urlString.substring(0, q) : (h >= 0 ? urlString.substring(0, h) : urlString);
        Query query = Query.EMPTY;
        if (q >= 0) {
            int end = h >= 0 ? h : urlString.length();
            query = Query.of(urlString.substring(q + 1, end));
        }
        Fragment fragment = h >= 0 ? new Fragment(urlString.substring(h + 1)) : Fragment.EMPTY;
        return new RelativeUrl(Path.of(path), query, fragment);
    }

    private static final class TestAutoAddressBarSyncComponent extends AutoAddressBarSyncComponent {
        private TestAutoAddressBarSyncComponent(RelativeUrl initialRelativeUrl) {
            super(initialRelativeUrl);
        }

        @Override
        public ComponentView<RelativeUrl> componentView() {
            return _ -> _ -> renderContext -> { };
        }
    }

    private static final class RecordingCommandsEnqueue implements CommandsEnqueue {
        private final List<Command> commands = new ArrayList<>();

        @Override
        public void offer(Command command) {
            commands.add(command);
        }

        private List<Command> commands() {
            return commands;
        }
    }

    private static final class RecordingStateUpdate implements StateUpdate<RelativeUrl> {
        private RelativeUrl state;

        private RecordingStateUpdate(RelativeUrl initialState) {
            this.state = initialState;
        }

        @Override
        public void setState(RelativeUrl newState) {
            state = newState;
        }

        @Override
        public void applyStateTransformation(UnaryOperator<RelativeUrl> stateTransformer) {
            state = stateTransformer.apply(state);
        }

        @Override
        public void applyStateTransformationIfPresent(Function<RelativeUrl, Optional<RelativeUrl>> stateTransformer) {
            stateTransformer.apply(state).ifPresent(updated -> state = updated);
        }

        private RelativeUrl state() {
            return state;
        }
    }

    private static final class RecordingSubscriber implements Subscriber {
        private final List<ComponentEventEntry> componentHandlers = new ArrayList<>();

        @Override
        public void addWindowEventHandler(String eventType,
                                          Consumer<EventContext> eventHandler,
                                          boolean preventDefault,
                                          DomEventEntry.Modifier modifier) {
            // Not used in this test.
        }

        @Override
        public Lookup.Registration addComponentEventHandler(String eventType,
                                                            Consumer<ComponentEventEntry.EventContext> eventHandler,
                                                            boolean preventDefault) {
            final ComponentEventEntry entry = new ComponentEventEntry(eventType, eventHandler, preventDefault);
            componentHandlers.add(entry);
            return () -> componentHandlers.removeIf(e -> e == entry);
        }

        private void emitComponentEvent(String eventType, Object payload) {
            for (ComponentEventEntry handler : componentHandlers) {
                if (handler.matches(eventType)) {
                    handler.eventHandler().accept(new ComponentEventEntry.EventContext(eventType, payload));
                }
            }
        }
    }
}
