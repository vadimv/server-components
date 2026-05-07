package rsp.compositions.contract;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentEventEntry;
import rsp.component.ContextKey;
import rsp.component.Lookup;
import rsp.component.StateUpdate;
import rsp.component.Subscriber;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.layout.GroupPlacementPolicy;
import rsp.compositions.layout.Placement;
import rsp.compositions.routing.AutoAddressBarSyncComponent;
import rsp.compositions.routing.Router;
import rsp.dom.DomEventEntry;
import rsp.page.EventContext;
import rsp.page.events.Command;
import rsp.page.events.ComponentEventNotification;
import rsp.server.http.Fragment;
import rsp.server.http.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.compositions.routing.AutoAddressBarSyncComponent.PathUpdateMode.PUSH_URL_ONLY;

/**
 * Tests for {@link SceneEventHandler} — particularly the inline return target
 * capture/restore flow used by inline forms.
 */
class SceneEventHandlerTests {

    @Nested
    class HandleShowInlineCaptures {

        @Test
        void captures_previous_routed_as_return_target() {
            final Composition composition = composition();
            final Scene initial = sceneWith(composition, ListContract.class);
            final RecordingSubscriber subscriber = new RecordingSubscriber();
            final RecordingStateUpdate<Scene> stateUpdate = new RecordingStateUpdate<>(initial);

            new SceneEventHandler(savedContextWithUrl(Query.EMPTY, ""))
                    .registerHandlers(initial, subscriber, NO_OP_COMMANDS, stateUpdate);

            subscriber.fire(EventKeys.SHOW.name(),
                    new ActionBindings.ShowPayload(EditContract.class, Map.of("id", "5")));

            final Scene next = stateUpdate.current();
            assertEquals(EditContract.class, next.routedRuntime().contractClass(),
                    "inline placement replaces the routed runtime");
            assertNotNull(next.inlineReturnTarget(), "previous routed must be captured");
            assertEquals(ListContract.class, next.inlineReturnTarget().contractClass());
            assertEquals("/posts", next.inlineReturnTarget().route());
        }

        @Test
        void captures_query_state_at_show_time() {
            final Composition composition = composition();
            final Scene initial = sceneWith(composition, ListContract.class);
            final RecordingSubscriber subscriber = new RecordingSubscriber();
            final RecordingStateUpdate<Scene> stateUpdate = new RecordingStateUpdate<>(initial);

            final Query liveQuery = new Query(java.util.List.of(
                    new Query.Parameter("p", "2"),
                    new Query.Parameter("sort", "asc")));

            new SceneEventHandler(savedContextWithUrl(liveQuery, ""))
                    .registerHandlers(initial, subscriber, NO_OP_COMMANDS, stateUpdate);

            subscriber.fire(EventKeys.SHOW.name(),
                    new ActionBindings.ShowPayload(EditContract.class, Map.of()));

            final Scene.InlineReturnTarget target = stateUpdate.current().inlineReturnTarget();
            assertNotNull(target);
            assertEquals(2, target.query().parameters().size());
            assertEquals("2", target.query().parameterValue("p"));
            assertEquals("asc", target.query().parameterValue("sort"));
        }

        @Test
        void captures_fragment_at_show_time() {
            final Composition composition = composition();
            final Scene initial = sceneWith(composition, ListContract.class);
            final RecordingSubscriber subscriber = new RecordingSubscriber();
            final RecordingStateUpdate<Scene> stateUpdate = new RecordingStateUpdate<>(initial);

            new SceneEventHandler(savedContextWithUrl(Query.EMPTY, "section-3"))
                    .registerHandlers(initial, subscriber, NO_OP_COMMANDS, stateUpdate);

            subscriber.fire(EventKeys.SHOW.name(),
                    new ActionBindings.ShowPayload(EditContract.class, Map.of()));

            final Scene.InlineReturnTarget target = stateUpdate.current().inlineReturnTarget();
            assertNotNull(target);
            assertEquals("section-3", target.fragment().fragmentString());
        }

        @Test
        void no_capture_when_no_previous_routed() {
            final Composition composition = composition();
            final Scene initial = sceneWith(composition, null);
            final RecordingSubscriber subscriber = new RecordingSubscriber();
            final RecordingStateUpdate<Scene> stateUpdate = new RecordingStateUpdate<>(initial);

            new SceneEventHandler(savedContextWithUrl(Query.EMPTY, ""))
                    .registerHandlers(initial, subscriber, NO_OP_COMMANDS, stateUpdate);

            subscriber.fire(EventKeys.SHOW.name(),
                    new ActionBindings.ShowPayload(EditContract.class, Map.of()));

            assertEquals(EditContract.class, stateUpdate.current().routedRuntime().contractClass());
            assertNull(stateUpdate.current().inlineReturnTarget(),
                    "no previous routed → nothing to return to");
        }

        @Test
        void no_capture_when_previous_route_unknown() {
            // RoutelessContract is bound but has no route registered.
            final Composition composition = compositionWithRoutelessContract();
            final Scene initial = sceneWith(composition, RoutelessContract.class);
            final RecordingSubscriber subscriber = new RecordingSubscriber();
            final RecordingStateUpdate<Scene> stateUpdate = new RecordingStateUpdate<>(initial);

            new SceneEventHandler(savedContextWithUrl(Query.EMPTY, ""))
                    .registerHandlers(initial, subscriber, NO_OP_COMMANDS, stateUpdate);

            subscriber.fire(EventKeys.SHOW.name(),
                    new ActionBindings.ShowPayload(EditContract.class, Map.of()));

            assertNull(stateUpdate.current().inlineReturnTarget(),
                    "previous routed had no route → cannot construct return target");
        }
    }

    @Nested
    class HandleActionSuccess {

        @Test
        void with_return_target_restores_previous_routed() {
            final Composition composition = composition();
            final Scene afterShow = sceneWith(composition, EditContract.class)
                    .withInlineReturnTarget(new Scene.InlineReturnTarget(
                            ListContract.class, "/posts", Query.EMPTY, Fragment.EMPTY));
            final RecordingSubscriber subscriber = new RecordingSubscriber();
            final RecordingStateUpdate<Scene> stateUpdate = new RecordingStateUpdate<>(afterShow);

            new SceneEventHandler(savedContextWithUrl(Query.EMPTY, ""))
                    .registerHandlers(afterShow, subscriber, NO_OP_COMMANDS, stateUpdate);

            subscriber.fire(EventKeys.ACTION_SUCCESS.name(),
                    new EventKeys.ActionResult(EditContract.class));

            final Scene next = stateUpdate.current();
            assertEquals(ListContract.class, next.routedRuntime().contractClass(),
                    "ACTION_SUCCESS restores the previous routed contract");
        }

        @Test
        void with_return_target_clears_the_target_on_restore() {
            final Composition composition = composition();
            final Scene afterShow = sceneWith(composition, EditContract.class)
                    .withInlineReturnTarget(new Scene.InlineReturnTarget(
                            ListContract.class, "/posts", Query.EMPTY, Fragment.EMPTY));
            final RecordingSubscriber subscriber = new RecordingSubscriber();
            final RecordingStateUpdate<Scene> stateUpdate = new RecordingStateUpdate<>(afterShow);

            new SceneEventHandler(savedContextWithUrl(Query.EMPTY, ""))
                    .registerHandlers(afterShow, subscriber, NO_OP_COMMANDS, stateUpdate);

            subscriber.fire(EventKeys.ACTION_SUCCESS.name(),
                    new EventKeys.ActionResult(EditContract.class));

            assertNull(stateUpdate.current().inlineReturnTarget(),
                    "return target is cleared once consumed");
        }

        @Test
        void with_return_target_publishes_return_url_as_scene_local_decoration() {
            final Composition composition = composition();
            final Scene afterShow = sceneWith(composition, EditContract.class)
                    .withInlineReturnTarget(new Scene.InlineReturnTarget(
                            ListContract.class, "/posts", Query.of("p=2"), Fragment.EMPTY));
            final RecordingSubscriber subscriber = new RecordingSubscriber();
            final RecordingStateUpdate<Scene> stateUpdate = new RecordingStateUpdate<>(afterShow);
            final RecordingCommands commands = new RecordingCommands();

            new SceneEventHandler(savedContextWithUrl(Query.EMPTY, ""))
                    .registerHandlers(afterShow, subscriber, commands, stateUpdate);

            subscriber.fire(EventKeys.ACTION_SUCCESS.name(),
                    new EventKeys.ActionResult(EditContract.class));

            final AutoAddressBarSyncComponent.PathUpdate update = commands.onlyPathUpdate();
            assertEquals("/posts?p=2", update.url().toString());
            assertEquals(PUSH_URL_ONLY, update.mode(),
                    "the scene already restored the runtime; the URL update must not ask routing to do it again");
        }

        @Test
        void without_return_target_falls_back_to_in_place_refresh() {
            // Existing behaviour: when no inline return target, refresh the routed in place.
            final Composition composition = composition();
            final Scene routed = sceneWith(composition, ListContract.class);
            final RecordingSubscriber subscriber = new RecordingSubscriber();
            final RecordingStateUpdate<Scene> stateUpdate = new RecordingStateUpdate<>(routed);

            new SceneEventHandler(savedContextWithUrl(Query.EMPTY, ""))
                    .registerHandlers(routed, subscriber, NO_OP_COMMANDS, stateUpdate);

            subscriber.fire(EventKeys.ACTION_SUCCESS.name(),
                    new EventKeys.ActionResult(ListContract.class));

            final Scene next = stateUpdate.current();
            assertEquals(ListContract.class, next.routedRuntime().contractClass(),
                    "in-place refresh keeps the same routed contract class");
            assertNull(next.inlineReturnTarget());
        }

        @Test
        void result_for_different_contract_class_does_not_trigger_restore() {
            // A successful action from a layered contract (different class than the routed inline
            // form) should NOT restore — only matching-class actions restore.
            final Composition composition = composition();
            final Scene afterShow = sceneWith(composition, EditContract.class)
                    .withInlineReturnTarget(new Scene.InlineReturnTarget(
                            ListContract.class, "/posts", Query.EMPTY, Fragment.EMPTY));
            final RecordingSubscriber subscriber = new RecordingSubscriber();
            final RecordingStateUpdate<Scene> stateUpdate = new RecordingStateUpdate<>(afterShow);

            new SceneEventHandler(savedContextWithUrl(Query.EMPTY, ""))
                    .registerHandlers(afterShow, subscriber, NO_OP_COMMANDS, stateUpdate);

            // Action came from RoutelessContract, not the inline form.
            subscriber.fire(EventKeys.ACTION_SUCCESS.name(),
                    new EventKeys.ActionResult(RoutelessContract.class));

            assertEquals(EditContract.class, stateUpdate.current().routedRuntime().contractClass(),
                    "non-matching contract class does not consume the return target");
            assertNotNull(stateUpdate.current().inlineReturnTarget(),
                    "return target is preserved until the inline form's own ACTION_SUCCESS");
        }
    }

    @Nested
    class HandleSetPrimary {

        @Test
        void clears_inline_return_target() {
            final Composition composition = composition();
            final Scene afterShow = sceneWith(composition, EditContract.class)
                    .withInlineReturnTarget(new Scene.InlineReturnTarget(
                            ListContract.class, "/posts", Query.EMPTY, Fragment.EMPTY));
            final RecordingSubscriber subscriber = new RecordingSubscriber();
            final RecordingStateUpdate<Scene> stateUpdate = new RecordingStateUpdate<>(afterShow);

            new SceneEventHandler(savedContextWithUrl(Query.EMPTY, ""))
                    .registerHandlers(afterShow, subscriber, NO_OP_COMMANDS, stateUpdate);

            subscriber.fire(EventKeys.SET_PRIMARY.name(), CommentsContract.class);

            assertEquals(CommentsContract.class, stateUpdate.current().routedRuntime().contractClass());
            assertNull(stateUpdate.current().inlineReturnTarget(),
                    "SET_PRIMARY is a fresh navigation — return target must be cleared");
        }
    }

    // --- Fixtures ----------------------------------------------------------

    private Composition composition() {
        // Default group policy is ALL_INLINE so SHOW resolves to inline reliably.
        final DefaultLayout layout = new DefaultLayout()
                .placement(EditContract.class, Placement.INLINE.primary())
                .placement(ListContract.class, Placement.INLINE.primary())
                .placement(CommentsContract.class, Placement.INLINE.primary());
        final Group group = new Group("Posts")
                .bind(ListContract.class, ListContract::new, () -> null)
                .bind(EditContract.class, EditContract::new, () -> null)
                .bind(CommentsContract.class, CommentsContract::new, () -> null);
        final Router router = new Router()
                .route("/posts", ListContract.class)
                .route("/posts/:id", EditContract.class)
                .route("/comments", CommentsContract.class);
        return new Composition(router, layout, group);
    }

    private Composition compositionWithRoutelessContract() {
        // RoutelessContract is bound but has no route — used to verify the
        // "no route on previous routed" branch of captureInlineReturnTarget.
        final DefaultLayout layout = new DefaultLayout()
                .groupPlacementPolicy(GroupPlacementPolicy.ALL_INLINE);
        final Group group = new Group("Posts")
                .bind(ListContract.class, ListContract::new, () -> null)
                .bind(EditContract.class, EditContract::new, () -> null)
                .bind(RoutelessContract.class, RoutelessContract::new, () -> null);
        final Router router = new Router()
                .route("/posts", ListContract.class)
                .route("/posts/:id", EditContract.class);
        return new Composition(router, layout, group);
    }

    /**
     * Build a Scene with the given routed contract class, and lazy factories for
     * everything else in the composition's group.
     */
    private Scene sceneWith(Composition composition, Class<? extends ViewContract> routedClass) {
        ContractRuntime routedRuntime = null;
        if (routedClass != null) {
            Function<Lookup, ViewContract> factory = composition.contracts().contractFactory(routedClass);
            routedRuntime = ContractRuntime.instantiate(routedClass, factory, baseContext());
        }
        // Lazy factories for all bound contracts other than the routed one.
        Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> lazy = new HashMap<>();
        for (Class<? extends ViewContract> cls : composition.contracts().contractClasses()) {
            if (!cls.equals(routedClass)) {
                lazy.put(cls, composition.contracts().contractFactory(cls));
            }
        }
        return Scene.of(routedRuntime, Map.of(), lazy, composition);
    }

    private ComponentContext baseContext() {
        return new ComponentContext()
                .with(CommandsEnqueue.class, NO_OP_COMMANDS)
                .with(Subscriber.class, new NoOpSubscriber());
    }

    /**
     * Build a savedContext mimicking what SceneComponent passes to SceneEventHandler:
     * a context with URL state populated by AutoAddressBarSyncComponent.
     */
    private ComponentContext savedContextWithUrl(Query query, String fragmentString) {
        ComponentContext ctx = baseContext();
        for (Query.Parameter param : query.parameters()) {
            ctx = ctx.with(
                    new ContextKey.StringKey<>("url.query." + param.name(), String.class),
                    param.value());
        }
        if (fragmentString != null && !fragmentString.isEmpty()) {
            ctx = ctx.with(ContextKeys.URL_FRAGMENT, fragmentString);
        }
        return ctx;
    }

    private static final CommandsEnqueue NO_OP_COMMANDS = _ -> {};

    private static final class RecordingCommands implements CommandsEnqueue {
        private final List<Command> commands = new ArrayList<>();

        @Override
        public void offer(Command command) {
            commands.add(command);
        }

        private AutoAddressBarSyncComponent.PathUpdate onlyPathUpdate() {
            final List<AutoAddressBarSyncComponent.PathUpdate> updates = commands.stream()
                    .filter(ComponentEventNotification.class::isInstance)
                    .map(ComponentEventNotification.class::cast)
                    .filter(notification -> AutoAddressBarSyncComponent.SET_PATH.name()
                            .equals(notification.eventType()))
                    .map(ComponentEventNotification::eventObject)
                    .filter(AutoAddressBarSyncComponent.PathUpdate.class::isInstance)
                    .map(AutoAddressBarSyncComponent.PathUpdate.class::cast)
                    .toList();
            assertEquals(1, updates.size(), "expected exactly one SET_PATH update");
            return updates.getFirst();
        }
    }

    // --- Test contracts ----------------------------------------------------

    static class ListContract extends ViewContract {
        ListContract(Lookup lookup) { super(lookup); }
        @Override public ComponentContext enrichContext(ComponentContext c) { return c; }
        @Override public String title() { return "List"; }
    }

    static class EditContract extends ViewContract {
        EditContract(Lookup lookup) { super(lookup); }
        @Override public ComponentContext enrichContext(ComponentContext c) { return c; }
        @Override public String title() { return "Edit"; }
    }

    static class CommentsContract extends ViewContract {
        CommentsContract(Lookup lookup) { super(lookup); }
        @Override public ComponentContext enrichContext(ComponentContext c) { return c; }
        @Override public String title() { return "Comments"; }
    }

    static class RoutelessContract extends ViewContract {
        RoutelessContract(Lookup lookup) { super(lookup); }
        @Override public ComponentContext enrichContext(ComponentContext c) { return c; }
        @Override public String title() { return "Routeless"; }
    }

    // --- Test helpers ------------------------------------------------------

    private static final class RecordingSubscriber implements Subscriber {
        private final Map<String, Consumer<ComponentEventEntry.EventContext>> handlers = new LinkedHashMap<>();

        @Override
        public void addWindowEventHandler(String eventType,
                                          Consumer<EventContext> eventHandler,
                                          boolean preventDefault,
                                          DomEventEntry.Modifier modifier) {}

        @Override
        public Lookup.Registration addComponentEventHandler(String eventType,
                                                            Consumer<ComponentEventEntry.EventContext> eventHandler,
                                                            boolean preventDefault) {
            handlers.put(eventType, eventHandler);
            return () -> handlers.remove(eventType);
        }

        void fire(String eventName, Object payload) {
            Consumer<ComponentEventEntry.EventContext> handler = handlers.get(eventName);
            assertTrue(handler != null,
                    "no handler registered for event \"" + eventName + "\"");
            handler.accept(new ComponentEventEntry.EventContext(eventName, payload));
        }
    }

    private static final class NoOpSubscriber implements Subscriber {
        @Override
        public void addWindowEventHandler(String eventType,
                                          Consumer<EventContext> eventHandler,
                                          boolean preventDefault,
                                          DomEventEntry.Modifier modifier) {}

        @Override
        public Lookup.Registration addComponentEventHandler(String eventType,
                                                            Consumer<ComponentEventEntry.EventContext> eventHandler,
                                                            boolean preventDefault) {
            return () -> {};
        }
    }

    private static final class RecordingStateUpdate<S> implements StateUpdate<S> {
        private S current;

        RecordingStateUpdate(S initial) {
            this.current = initial;
        }

        S current() {
            return current;
        }

        @Override
        public void setState(S newState) {
            this.current = newState;
        }

        @Override
        public void applyStateTransformation(UnaryOperator<S> stateTransformer) {
            this.current = stateTransformer.apply(this.current);
        }

        @Override
        public void applyStateTransformationIfPresent(Function<S, Optional<S>> stateTransformer) {
            stateTransformer.apply(this.current).ifPresent(s -> this.current = s);
        }
    }
}
