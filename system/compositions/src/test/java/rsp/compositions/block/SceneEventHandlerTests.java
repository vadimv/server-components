package rsp.compositions.block;

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
import static org.junit.jupiter.api.Assertions.assertSame;

/** Scene events select direct block descriptors; hosts create the components later. */
class SceneEventHandlerTests {
    private static final CommandsEnqueue NO_OP_COMMANDS = _ -> {};

    @Test
    void inline_show_replaces_the_primary_descriptor_and_captures_return_target() {
        Scene initial = scene(ListBlock.class);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        RecordingStateUpdater stateUpdate = new RecordingStateUpdater(initial);

        new SceneEventHandler(savedContext()).registerHandlers(initial, subscriber, NO_OP_COMMANDS, stateUpdate);
        subscriber.fire(EventKeys.SHOW.name(), new ActionBindings.ShowPayload(EditBlock.class, Map.of("id", "5")));

        assertEquals(EditBlock.class, stateUpdate.current().routedDescriptor().blockClass());
        assertNotNull(stateUpdate.current().inlineReturnTarget());
        assertEquals(ListBlock.class, stateUpdate.current().inlineReturnTarget().blockClass());
    }

    @Test
    void action_success_restores_the_captured_primary_descriptor() {
        Scene inlineScene = scene(EditBlock.class).withInlineReturnTarget(
                new Scene.InlineReturnTarget(ListBlock.class, "/posts", Query.EMPTY,
                        rsp.server.http.Fragment.EMPTY));
        RecordingSubscriber subscriber = new RecordingSubscriber();
        RecordingStateUpdater stateUpdate = new RecordingStateUpdater(inlineScene);

        new SceneEventHandler(savedContext()).registerHandlers(inlineScene, subscriber, NO_OP_COMMANDS, stateUpdate);
        subscriber.fire(EventKeys.ACTION_SUCCESS.name(), new EventKeys.ActionResult(EditBlock.class));

        assertEquals(ListBlock.class, stateUpdate.current().routedDescriptor().blockClass());
        assertNull(stateUpdate.current().inlineReturnTarget());
    }

    @Test
    void set_primary_selects_a_bound_direct_block() {
        Scene initial = scene(ListBlock.class);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        RecordingStateUpdater stateUpdate = new RecordingStateUpdater(initial);

        new SceneEventHandler(savedContext()).registerHandlers(initial, subscriber, NO_OP_COMMANDS, stateUpdate);
        subscriber.fire(EventKeys.SET_PRIMARY.name(), CommentsBlock.class);

        assertEquals(CommentsBlock.class, stateUpdate.current().routedDescriptor().blockClass());
    }

    @Test
    void set_primary_distinguishes_object_keys_bound_to_the_same_block_class() {
        Object postsKey = new Object();
        Object archivedKey = new Object();
        DefaultLayout layout = new DefaultLayout()
                .placement(ListBlock.class, Placement.INLINE.primary());
        Group group = new Group("Posts")
                .bind(postsKey, ListBlock.class, ListBlock::new)
                .bind(archivedKey, ListBlock.class, ListBlock::new);
        Composition composition = new Composition(new Router()
                .route("/posts", postsKey)
                .route("/archive", archivedKey), layout, group);
        Scene initial = Scene.of(
                BlockDescriptor.forBlock(postsKey, ListBlock.class, Map.of()),
                Map.of(), composition);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        RecordingStateUpdater stateUpdate = new RecordingStateUpdater(initial);

        new SceneEventHandler(savedContext()).registerHandlers(
                initial, subscriber, NO_OP_COMMANDS, stateUpdate);
        subscriber.fire(EventKeys.SET_PRIMARY.name(), archivedKey);

        assertSame(archivedKey, stateUpdate.current().routedDescriptor().blockKey());
        assertEquals(ListBlock.class, stateUpdate.current().routedDescriptor().blockClass());
    }

    @Test
    void title_updates_only_apply_to_the_active_descriptor() {
        Scene initial = scene(ListBlock.class);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        RecordingStateUpdater stateUpdate = new RecordingStateUpdater(initial);

        new SceneEventHandler(savedContext()).registerHandlers(initial, subscriber, NO_OP_COMMANDS, stateUpdate);
        subscriber.fire(EventKeys.SCENE_TITLE_UPDATED.name(),
                new EventKeys.SceneTitleUpdate(initial.routedDescriptor().instanceId(), "Posts"));

        assertEquals("Posts", stateUpdate.current().pageTitle());
    }

    private Scene scene(Class<? extends Block<?, ?>> routed) {
        DefaultLayout layout = new DefaultLayout()
                .placement(EditBlock.class, Placement.INLINE.primary())
                .placement(CommentsBlock.class, Placement.INLINE.primary());
        Group group = new Group("Posts")
                .bind(ListBlock.class, ListBlock::new)
                .bind(EditBlock.class, EditBlock::new)
                .bind(CommentsBlock.class, CommentsBlock::new);
        Composition composition = new Composition(new Router()
                .route("/posts", ListBlock.class)
                .route("/posts/:id", EditBlock.class)
                .route("/comments", CommentsBlock.class), layout, group);
        return Scene.of(BlockDescriptor.forBlock(routed, Map.of()), Map.of(), composition);
    }

    private ComponentContext savedContext() {
        return new ComponentContext()
                .with(CommandsEnqueue.class, NO_OP_COMMANDS)
                .with(Subscriber.class, new NoOpSubscriber())
                .with(ContextKeys.URL_PATH_FULL, rsp.server.Path.of("/posts"))
                .with(new ContextKey.StringKey<>("url.query.p", String.class), "2");
    }

    static class TestBlock extends Block<String, Object> {
        @Override public ComponentStateSupplier<String> initStateSupplier() { return (_, _) -> "ready"; }
        @Override public ComponentView<String, Object> componentView() { return _ -> _ -> null; }
        @Override public String title() { return "Test"; }
    }

    static class ListBlock extends TestBlock {}
    static class EditBlock extends TestBlock {}
    static class CommentsBlock extends TestBlock {}

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
