package rsp.page;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.*;
import rsp.dom.DomEventEntry;
import rsp.dom.TreePositionPath;
import rsp.dom.XmlNs;
import rsp.page.events.*;
import rsp.server.TestCollectingRemoteOut;
import rsp.util.json.JsonDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class LivePageSessionTests {

    private static final QualifiedSessionId SESSION_ID = new QualifiedSessionId("test-device", "test-session");

    private ManualEventLoop eventLoop;
    private LivePageSession session;
    private TestCollectingRemoteOut remoteOut;
    private RedirectableEventsConsumer commandsEnqueue;

    @BeforeEach
    void setUp() {
        eventLoop = new ManualEventLoop();
        session = new LivePageSession(eventLoop);
        remoteOut = new TestCollectingRemoteOut();
        commandsEnqueue = new RedirectableEventsConsumer();
    }

    private PageBuilder createPageBuilder() {
        return new PageBuilder(SESSION_ID, "/* test config */", new ComponentContext(), commandsEnqueue);
    }

    private void initSession(final PageBuilder pageBuilder) {
        session.start();
        session.eventsConsumer().accept(new InitSessionCommand(pageBuilder, commandsEnqueue, remoteOut));
        eventLoop.runOneStep();
    }

    private void processEvent(final Command event) {
        session.eventsConsumer().accept(event);
        eventLoop.runOneStep();
    }

    /**
     * A manual, deterministic EventLoop for testing.
     */
    static class ManualEventLoop implements EventLoop {
        private Runnable step;
        private boolean stopped = false;

        @Override
        public void start(final Runnable logic) {
            this.step = logic;
        }

        @Override
        public void stop() {
            this.stopped = true;
        }

        public void runOneStep() {
            if (step == null) {
                throw new IllegalStateException("EventLoop not started");
            }
            if (!stopped) {
                step.run();
            }
        }

        public boolean isStopped() {
            return stopped;
        }
    }

    @Nested
    public class InitializationTests {

        @Test
        void init_session_with_empty_page_completes_successfully() {
            final PageBuilder pageBuilder = createPageBuilder();

            initSession(pageBuilder);

            // No listen event messages for empty page
            final List<TestCollectingRemoteOut.ListenEventOutMessage> listenMessages = remoteOut.commands.stream()
                    .filter(m -> m instanceof TestCollectingRemoteOut.ListenEventOutMessage)
                    .map(m -> (TestCollectingRemoteOut.ListenEventOutMessage) m)
                    .toList();

            assertTrue(listenMessages.isEmpty());
        }

        @Test
        void init_session_redirects_commands_to_reactor() {
            final PageBuilder pageBuilder = createPageBuilder();

            initSession(pageBuilder);

            // After init, commands should go through the reactor
            commandsEnqueue.accept(new RemoteCommand.PushHistory("/test-path"));
            eventLoop.runOneStep();

            final boolean hasPushHistory = remoteOut.commands.stream()
                    .anyMatch(m -> m instanceof TestCollectingRemoteOut.PushHistoryMessage pm
                                   && pm.path().equals("/test-path"));
            assertTrue(hasPushHistory, "Command should be routed through reactor after init");
        }
    }

    @Nested
    public class ShutdownTests {

        @Test
        void shutdown_command_stops_event_loop() {
            final PageBuilder pageBuilder = createPageBuilder();
            initSession(pageBuilder);

            processEvent(new ShutdownSessionCommand());

            assertTrue(eventLoop.isStopped());
        }
    }

    @Nested
    public class RemoteCommandTests {

        @Test
        void remote_command_push_history_is_forwarded_to_remote_out() {
            final PageBuilder pageBuilder = createPageBuilder();
            initSession(pageBuilder);
            remoteOut.clear();

            processEvent(new RemoteCommand.PushHistory("/new-path"));

            final boolean hasPushHistory = remoteOut.commands.stream()
                    .anyMatch(m -> m instanceof TestCollectingRemoteOut.PushHistoryMessage pm
                                   && pm.path().equals("/new-path"));
            assertTrue(hasPushHistory);
        }

        @Test
        void remote_command_eval_js_is_forwarded_to_remote_out() {
            final PageBuilder pageBuilder = createPageBuilder();
            initSession(pageBuilder);
            remoteOut.clear();

            processEvent(new RemoteCommand.EvalJs(1, "console.log('test')"));

            final List<TestCollectingRemoteOut.EvalJsMessage> evalMessages = remoteOut.commands.stream()
                    .filter(m -> m instanceof TestCollectingRemoteOut.EvalJsMessage)
                    .map(m -> (TestCollectingRemoteOut.EvalJsMessage) m)
                    .toList();

            assertEquals(1, evalMessages.size());
            assertEquals("console.log('test')", evalMessages.get(0).js());
        }
    }

    @Nested
    public class JavaScriptEvaluationTests {

        @Test
        void eval_js_response_with_unknown_descriptor_is_ignored() {
            final PageBuilder pageBuilder = createPageBuilder();
            initSession(pageBuilder);

            // Send response for non-existent descriptor - should not throw
            processEvent(new EvalJsResponseEvent(999, new JsonDataType.String("ignored")));

            // No exception means success
        }
    }

    @Nested
    public class GenericTaskTests {

        @Test
        void generic_task_event_executes_the_task() {
            final PageBuilder pageBuilder = createPageBuilder();
            initSession(pageBuilder);

            final List<String> executed = new ArrayList<>();

            processEvent(new GenericTaskEvent(() -> executed.add("task-ran")));

            assertEquals(1, executed.size());
            assertEquals("task-ran", executed.get(0));
        }

        @Test
        void multiple_generic_tasks_execute_in_order() {
            final PageBuilder pageBuilder = createPageBuilder();
            initSession(pageBuilder);

            final List<String> executed = new ArrayList<>();

            processEvent(new GenericTaskEvent(() -> executed.add("first")));
            processEvent(new GenericTaskEvent(() -> executed.add("second")));
            processEvent(new GenericTaskEvent(() -> executed.add("third")));

            assertEquals(List.of("first", "second", "third"), executed);
        }
    }

    @Nested
    public class ComponentEventTests {

        @Test
        void component_event_notification_with_no_handlers_completes_without_error() {
            final PageBuilder pageBuilder = createPageBuilder();
            initSession(pageBuilder);

            // Component events are dispatched to registered handlers in pageBuilder
            // Without handlers, this should complete without error
            processEvent(new ComponentEventNotification("testEvent", "payload"));

            // No exception means the event was processed
        }
    }

    @Nested
    public class DomEventTests {

        @Test
        void dom_event_with_no_handlers_completes_without_error() {
            final PageBuilder pageBuilder = createPageBuilder();
            initSession(pageBuilder);

            // DOM events with no matching handlers should complete without error
            processEvent(new DomEventNotification(0,
                                                  TreePositionPath.of("1_1"),
                                                  "click",
                                                  JsonDataType.Object.EMPTY));

            // No exception means the event was processed
        }

        @Test
        void dom_event_invokes_matching_handler() {
            final PageBuilder pageBuilder = createPageBuilder();
            final List<String> invocations = new ArrayList<>();
            final TreePositionPath elementPath = TreePositionPath.of("1_1");

            // Set up a component with a DOM event handler
            final ComponentSegment<String> segment = createComponentWithDomEventHandler(
                    pageBuilder,
                    elementPath,
                    "click",
                    ctx -> invocations.add("clicked")
            );
            renderComponent(pageBuilder, segment);
            initSession(pageBuilder);

            // Fire the event
            processEvent(new DomEventNotification(0,
                                                  elementPath,
                                                  "click",
                                                  JsonDataType.Object.EMPTY));

            assertEquals(1, invocations.size());
            assertEquals("clicked", invocations.get(0));
        }

        @Test
        void dom_event_bubbles_to_parent_element() {
            final PageBuilder pageBuilder = createPageBuilder();
            final List<String> invocations = new ArrayList<>();
            final TreePositionPath parentPath = TreePositionPath.of("1_1");
            final TreePositionPath childPath = TreePositionPath.of("1_1_1");

            // Handler on parent element
            final ComponentSegment<String> segment = createComponentWithDomEventHandler(
                    pageBuilder,
                    parentPath,
                    "click",
                    ctx -> invocations.add("parent-clicked")
            );
            renderComponent(pageBuilder, segment);
            initSession(pageBuilder);

            // Fire event on child element - should bubble to parent
            processEvent(new DomEventNotification(0,
                                                  childPath,
                                                  "click",
                                                  JsonDataType.Object.EMPTY));

            assertEquals(1, invocations.size());
            assertEquals("parent-clicked", invocations.get(0));
        }
    }

    @Nested
    public class ComponentEventWithHandlerTests {

        @Test
        void component_event_invokes_matching_handler() {
            final PageBuilder pageBuilder = createPageBuilder();
            final List<ComponentEventEntry.EventContext> invocations = new ArrayList<>();

            // Set up a component with a component event handler
            final ComponentSegment<String> segment = createComponentWithComponentEventHandler(
                    pageBuilder,
                    "testEvent",
                    invocations::add
            );
            renderComponent(pageBuilder, segment);
            initSession(pageBuilder);

            // Fire the component event
            processEvent(new ComponentEventNotification("testEvent", "eventPayload"));

            assertEquals(1, invocations.size());
            assertEquals("testEvent", invocations.get(0).eventName());
            assertEquals("eventPayload", invocations.get(0).eventObject());
        }

        @Test
        void component_event_wildcard_handler_matches_prefix() {
            final PageBuilder pageBuilder = createPageBuilder();
            final List<ComponentEventEntry.EventContext> invocations = new ArrayList<>();

            // Set up a wildcard handler
            final ComponentSegment<String> segment = createComponentWithComponentEventHandler(
                    pageBuilder,
                    "stateUpdated.*",
                    invocations::add
            );
            renderComponent(pageBuilder, segment);
            initSession(pageBuilder);

            // Fire an event that matches the wildcard pattern
            processEvent(new ComponentEventNotification("stateUpdated.sort", "sortValue"));

            assertEquals(1, invocations.size());
            assertEquals("stateUpdated.sort", invocations.get(0).eventName());
        }
    }

    // Helper methods for creating components with event handlers

    private ComponentSegment<String> createComponentWithDomEventHandler(final PageBuilder pageBuilder,
                                                                         final TreePositionPath elementPath,
                                                                         final String eventType,
                                                                         final Consumer<EventContext> handler) {
        final ComponentCompositeKey componentId = new ComponentCompositeKey(
                SESSION_ID,
                "testType",
                TreePositionPath.of("1")
        );
        final ComponentStateSupplier<String> stateSupplier = (key, ctx) -> "state";
        final BiFunction<ComponentContext, String, ComponentContext> contextResolver = (ctx, state) -> ctx;
        final ComponentView<String> componentView = stateUpdate -> state -> renderContext -> {
            renderContext.openNode(XmlNs.html, "div", false);
            renderContext.addEvent(elementPath, eventType, handler, false, DomEventEntry.NO_MODIFIER);
            renderContext.closeNode("div", false);
        };

        return new ComponentSegment<>(
                componentId,
                stateSupplier,
                contextResolver,
                componentView,
                new NoOpCallbacks(),
                pageBuilder,
                new ComponentContext(),
                commandsEnqueue
        );
    }

    private ComponentSegment<String> createComponentWithComponentEventHandler(final PageBuilder pageBuilder,
                                                                               final String eventType,
                                                                               final Consumer<ComponentEventEntry.EventContext> handler) {
        final ComponentCompositeKey componentId = new ComponentCompositeKey(
                SESSION_ID,
                "testType",
                TreePositionPath.of("1")
        );
        final ComponentStateSupplier<String> stateSupplier = (key, ctx) -> "state";
        final BiFunction<ComponentContext, String, ComponentContext> contextResolver = (ctx, state) -> ctx;
        final ComponentView<String> componentView = stateUpdate -> state -> renderContext -> {
            renderContext.openNode(XmlNs.html, "div", false);
            renderContext.closeNode("div", false);
        };

        final ComponentSegment<String> segment = new ComponentSegment<>(
                componentId,
                stateSupplier,
                contextResolver,
                componentView,
                new ComponentEventRegisteringCallbacks(eventType, handler),
                pageBuilder,
                new ComponentContext(),
                commandsEnqueue
        );
        return segment;
    }

    private void renderComponent(final PageBuilder pageBuilder, final ComponentSegment<String> segment) {
        pageBuilder.openComponent(segment);
        segment.render(pageBuilder);
        pageBuilder.closeComponent();
    }

    /**
     * No-op implementation of ComponentCallbacks for testing.
     */
    static class NoOpCallbacks implements ComponentCallbacks<String> {
        @Override
        public boolean onBeforeUpdated(final String newState, final Consumer<Command> commandsEnqueue) {
            return true;
        }

        @Override
        public void onAfterRendered(final String state, final Subscriber subscriber, final Consumer<Command> commandsEnqueue, final StateUpdate<String> stateUpdate) {
        }

        @Override
        public void onMounted(final ComponentCompositeKey componentId, final String state, final StateUpdate<String> stateUpdate) {
        }

        @Override
        public void onUpdated(final ComponentCompositeKey componentId, final String oldState, final String newState, final StateUpdate<String> stateUpdate) {
        }

        @Override
        public void onUnmounted(final ComponentCompositeKey componentId, final String state) {
        }
    }

    /**
     * Callbacks implementation that registers a component event handler during onAfterRendered.
     */
    static class ComponentEventRegisteringCallbacks extends NoOpCallbacks {
        private final String eventType;
        private final Consumer<ComponentEventEntry.EventContext> handler;

        ComponentEventRegisteringCallbacks(final String eventType, final Consumer<ComponentEventEntry.EventContext> handler) {
            this.eventType = eventType;
            this.handler = handler;
        }

        @Override
        public void onAfterRendered(final String state, final Subscriber subscriber, final Consumer<Command> commandsEnqueue, final StateUpdate<String> stateUpdate) {
            subscriber.addComponentEventHandler(eventType, handler, false);
        }
    }
}
