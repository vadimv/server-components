package rsp.compositions.contract;

import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentEventEntry;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.ContextKey;
import rsp.component.Lookup;
import rsp.component.StateUpdater;
import rsp.component.Subscriber;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.layout.Placement;
import rsp.compositions.routing.Router;
import rsp.dom.DomEventEntry;
import rsp.page.EventContext;
import rsp.server.http.Query;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Scene events select direct contract descriptors; hosts create the components later. */
class SceneEventHandlerTests {
    private static final CommandsEnqueue NO_OP_COMMANDS = _ -> {};

    @Test
    void inline_show_replaces_the_primary_descriptor_and_captures_return_target() {
        Scene initial = scene(ListContract.class);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        RecordingStateUpdater stateUpdate = new RecordingStateUpdater(initial);

        new SceneEventHandler(savedContext()).registerHandlers(initial, subscriber, NO_OP_COMMANDS, stateUpdate);
        subscriber.fire(EventKeys.SHOW.name(), new ActionBindings.ShowPayload(EditContract.class, Map.of("id", "5")));

        assertEquals(EditContract.class, stateUpdate.current().routedDescriptor().contractClass());
        assertNotNull(stateUpdate.current().inlineReturnTarget());
        assertEquals(ListContract.class, stateUpdate.current().inlineReturnTarget().contractClass());
    }

    @Test
    void action_success_restores_the_captured_primary_descriptor() {
        Scene inlineScene = scene(EditContract.class).withInlineReturnTarget(
                new Scene.InlineReturnTarget(ListContract.class, "/posts", Query.EMPTY,
                        rsp.server.http.Fragment.EMPTY));
        RecordingSubscriber subscriber = new RecordingSubscriber();
        RecordingStateUpdater stateUpdate = new RecordingStateUpdater(inlineScene);

        new SceneEventHandler(savedContext()).registerHandlers(inlineScene, subscriber, NO_OP_COMMANDS, stateUpdate);
        subscriber.fire(EventKeys.ACTION_SUCCESS.name(), new EventKeys.ActionResult(EditContract.class));

        assertEquals(ListContract.class, stateUpdate.current().routedDescriptor().contractClass());
        assertNull(stateUpdate.current().inlineReturnTarget());
    }

    @Test
    void set_primary_selects_a_bound_direct_contract() {
        Scene initial = scene(ListContract.class);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        RecordingStateUpdater stateUpdate = new RecordingStateUpdater(initial);

        new SceneEventHandler(savedContext()).registerHandlers(initial, subscriber, NO_OP_COMMANDS, stateUpdate);
        subscriber.fire(EventKeys.SET_PRIMARY.name(), CommentsContract.class);

        assertEquals(CommentsContract.class, stateUpdate.current().routedDescriptor().contractClass());
    }

    @Test
    void title_updates_only_apply_to_the_active_descriptor() {
        Scene initial = scene(ListContract.class);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        RecordingStateUpdater stateUpdate = new RecordingStateUpdater(initial);

        new SceneEventHandler(savedContext()).registerHandlers(initial, subscriber, NO_OP_COMMANDS, stateUpdate);
        subscriber.fire(EventKeys.SCENE_TITLE_UPDATED.name(),
                new EventKeys.SceneTitleUpdate(initial.routedDescriptor().instanceId(), "Posts"));

        assertEquals("Posts", stateUpdate.current().pageTitle());
    }

    private Scene scene(Class<? extends Contract> routed) {
        DefaultLayout layout = new DefaultLayout()
                .placement(EditContract.class, Placement.INLINE.primary())
                .placement(CommentsContract.class, Placement.INLINE.primary());
        Group group = new Group("Posts")
                .bind(ListContract.class, ListContract::new)
                .bind(EditContract.class, EditContract::new)
                .bind(CommentsContract.class, CommentsContract::new);
        Composition composition = new Composition(new Router()
                .route("/posts", ListContract.class)
                .route("/posts/:id", EditContract.class)
                .route("/comments", CommentsContract.class), layout, group);
        return Scene.of(ContractDescriptor.forContract(routed, Map.of()), Map.of(), composition);
    }

    private ComponentContext savedContext() {
        return new ComponentContext()
                .with(CommandsEnqueue.class, NO_OP_COMMANDS)
                .with(Subscriber.class, new NoOpSubscriber())
                .with(ContextKeys.URL_PATH_FULL, rsp.server.Path.of("/posts"))
                .with(new ContextKey.StringKey<>("url.query.p", String.class), "2");
    }

    static class TestContract extends ContractNodeComponent<String, Object> {
        @Override public ComponentStateSupplier<String> initStateSupplier() { return (_, _) -> "ready"; }
        @Override public ComponentView<String, Object> componentView() { return _ -> _ -> null; }
        @Override public String title() { return "Test"; }
    }

    static class ListContract extends TestContract {}
    static class EditContract extends TestContract {}
    static class CommentsContract extends TestContract {}

    private static final class RecordingSubscriber implements Subscriber {
        private final Map<String, Consumer<ComponentEventEntry.EventContext>> handlers = new LinkedHashMap<>();

        @Override
        public void addWindowEventHandler(String eventType, Consumer<EventContext> eventHandler,
                                          boolean preventDefault, DomEventEntry.Modifier modifier) {
        }

        @Override
        public Lookup.Registration addComponentEventHandler(String eventType,
                                                            Consumer<ComponentEventEntry.EventContext> handler,
                                                            boolean preventDefault) {
            handlers.put(eventType, handler);
            return () -> handlers.remove(eventType);
        }

        void fire(String eventType, Object payload) {
            handlers.get(eventType).accept(new ComponentEventEntry.EventContext(eventType, payload));
        }
    }

    private static final class NoOpSubscriber implements Subscriber {
        @Override
        public void addWindowEventHandler(String eventType, Consumer<EventContext> eventHandler,
                                          boolean preventDefault, DomEventEntry.Modifier modifier) {
        }

        @Override
        public Lookup.Registration addComponentEventHandler(String eventType,
                                                            Consumer<ComponentEventEntry.EventContext> handler,
                                                            boolean preventDefault) {
            return () -> {};
        }
    }

    private static final class RecordingStateUpdater implements StateUpdater<Scene> {
        private Scene current;

        RecordingStateUpdater(Scene initial) {
            current = initial;
        }

        Scene current() {
            return current;
        }

        @Override public void setState(Scene state) { current = state; }
        @Override public void applyStateTransformation(UnaryOperator<Scene> update) { current = update.apply(current); }
        @Override public void applyStateTransformationIfPresent(Function<Scene, Optional<Scene>> update) {
            update.apply(current).ifPresent(state -> current = state);
        }
    }
}
