package rsp.compositions.routing;

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
import static rsp.compositions.routing.AutoAddressBarSyncComponent.PathUpdateMode.UPDATE_PATH_ONLY;

class AutoAddressBarSyncComponentTests {

    @Test
    void query_update_after_update_path_only_uses_pending_path_as_base() {
        RelativeUrl initialUrl = new RelativeUrl(Path.of("/posts"), Query.of("p=2"), Fragment.EMPTY);
        TestAutoAddressBarSyncComponent component = new TestAutoAddressBarSyncComponent(initialUrl);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        RecordingCommandsEnqueue commandsEnqueue = new RecordingCommandsEnqueue();
        RecordingStateUpdate stateUpdate = new RecordingStateUpdate(initialUrl);

        component.onAfterRendered(initialUrl, subscriber, commandsEnqueue, stateUpdate);

        RelativeUrl commentsUrl = new RelativeUrl(Path.of("/comments"), Query.EMPTY, Fragment.EMPTY);
        subscriber.emitComponentEvent(
            AutoAddressBarSyncComponent.SET_PATH.name(),
            new AutoAddressBarSyncComponent.PathUpdate(commentsUrl, UPDATE_PATH_ONLY)
        );

        // UPDATE_PATH_ONLY intentionally avoids a re-render, so state stays stale until
        // the next URL mutation consumes pendingUrl.
        assertEquals("/posts?p=2", stateUpdate.state().toString());

        subscriber.emitComponentEvent(
            "stateUpdated.p",
            new ContextStateComponent.ContextValue.StringValue("3")
        );

        assertEquals("/comments?p=3", stateUpdate.state().toString());
        assertEquals(
            List.of(
                new RemoteCommand.PushHistory("/comments"),
                new RemoteCommand.PushHistory("/comments?p=3")
            ),
            commandsEnqueue.commands()
        );
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
        public void addComponentEventHandler(String eventType,
                                             Consumer<ComponentEventEntry.EventContext> eventHandler,
                                             boolean preventDefault) {
            componentHandlers.add(new ComponentEventEntry(eventType, eventHandler, preventDefault));
        }

        @Override
        public void removeComponentEventHandler(String eventType) {
            // Not needed for this test.
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
