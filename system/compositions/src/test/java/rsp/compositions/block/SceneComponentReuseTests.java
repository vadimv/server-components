package rsp.compositions.block;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentEventEntry;
import rsp.component.ComponentSegment;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.component.TreeBuilder;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.layout.Placement;
import rsp.compositions.routing.BlockRoutes;
import rsp.compositions.routing.UrlSyncComponent;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.ComponentEventNotification;
import rsp.page.events.GenericTaskEvent;
import rsp.url.Path;
import rsp.url.Fragment;
import rsp.url.Query;
import rsp.url.RelativeUrl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rsp.dsl.Html.div;

/** Regressions for direct block mounting from scene descriptors. */
class SceneComponentReuseTests {
    private final RecordingCommands commands = new RecordingCommands();

    @BeforeEach
    void resetCounters() {
        ListBlock.reset();
        OverlayBlock.reset();
    }

    @Test
    void directly_bound_block_opens_as_a_modal_layer() {
        ComponentSegment<RelativeUrl> root = renderApp(new DefaultLayout());

        assertEquals(1, ListBlock.mounted);
        emit(root, EventKeys.SHOW.name(), new ActionBindings.ShowPayload(OverlayBlock.class, Map.of("id", "7")));
        commands.drain(root);

        assertEquals(1, OverlayBlock.mounted);
    }

    @Test
    void directly_bound_block_opens_inline() {
        ComponentSegment<RelativeUrl> root = renderApp(
                new DefaultLayout().placement(OverlayBlock.class, Placement.INLINE.primary()));

        emit(root, EventKeys.SHOW.name(), new ActionBindings.ShowPayload(OverlayBlock.class, Map.of("id", "7")));
        commands.drain(root);

        assertEquals(1, OverlayBlock.mounted);
    }

    private ComponentSegment<RelativeUrl> renderApp(DefaultLayout layout) {
        Group group = new Group("Items")
                .bind(ListBlock.class, ListBlock::new)
                .bind(OverlayBlock.class, OverlayBlock::new);
        Composition composition = new Composition(BlockRoutes.builder()
                .route("/items", ListBlock.class)
                .route("/items/{id}", OverlayBlock.class), layout, group);
        UrlSyncComponent component = new UrlSyncComponent(
                new RelativeUrl(Path.of("/items"), Query.EMPTY, Fragment.EMPTY));
        TreeBuilder treeBuilder = new TreeBuilder(
                new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"),
                new ComponentContext()
                        .with(CommandsEnqueue.class, commands)
                        .with(ContextKeys.APP_COMPOSITIONS, List.of(composition)),
                commands);

        ComponentSegment<RelativeUrl> root = treeBuilder.openComponent(component);
        root.render(treeBuilder);
        treeBuilder.closeComponent();
        return root;
    }

    private static void emit(ComponentSegment<?> root, String eventName, Object payload) {
        for (ComponentEventEntry entry : root.recursiveComponentEvents()) {
            if (entry.matches(eventName)) {
                entry.eventHandler().accept(new ComponentEventEntry.EventContext(eventName, payload));
            }
        }
    }

    static final class ListBlock extends Block<String, Object> {
        static int mounted;

        static void reset() { mounted = 0; }
        @Override public ComponentStateSupplier<String> initStateSupplier() { return (_, _) -> "ready"; }
        @Override public ComponentView<String, Object> componentView() { return _ -> _ -> div(); }
        @Override public String title() { return "Items"; }
        @Override protected void onBlockMounted(String state, StateUpdater<String> stateUpdate) { mounted++; }
    }

    static final class OverlayBlock extends Block<String, Object> {
        static int mounted;

        static void reset() { mounted = 0; }
        @Override public ComponentStateSupplier<String> initStateSupplier() { return (_, _) -> "ready"; }
        @Override public ComponentView<String, Object> componentView() { return _ -> _ -> div(); }
        @Override public String title() { return "Overlay"; }
        @Override protected void onBlockMounted(String state, StateUpdater<String> stateUpdate) { mounted++; }
    }

    private static final class RecordingCommands implements CommandsEnqueue {
        private final List<Command> commands = new ArrayList<>();
        private int processed;

        @Override
        public void offer(Command command) {
            commands.add(command);
        }

        void drain(ComponentSegment<?> root) {
            while (processed < commands.size()) {
                Command command = commands.get(processed++);
                if (command instanceof ComponentEventNotification notification) {
                    emit(root, notification.eventType(), notification.eventObject());
                } else if (command instanceof GenericTaskEvent taskEvent) {
                    taskEvent.task().run();
                }
            }
        }
    }
}
