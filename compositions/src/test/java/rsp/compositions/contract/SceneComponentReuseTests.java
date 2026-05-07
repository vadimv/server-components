package rsp.compositions.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentEventEntry;
import rsp.component.ComponentSegment;
import rsp.component.Lookup;
import rsp.component.Subscriber;
import rsp.component.TreeBuilder;
import rsp.component.definitions.ContextStateComponent;
import rsp.component.definitions.StatelessComponent;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.layout.Placement;
import rsp.compositions.routing.AutoAddressBarSyncComponent;
import rsp.compositions.routing.Router;
import rsp.compositions.routing.UrlSyncComponent;
import rsp.dom.DomEventEntry;
import rsp.dom.TreePositionPath;
import rsp.page.EventContext;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.ComponentEventNotification;
import rsp.page.events.GenericTaskEvent;
import rsp.page.events.RemoteCommand;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rsp.dsl.Html.div;

/**
 * Regression tests for route-shell reuse.
 * <p>
 * Query-only URL updates should refresh downstream context without destroying
 * stable companion contracts such as prompt/right-sidebar panels.
 */
class SceneComponentReuseTests {
    private final RecordingCommands commands = new RecordingCommands();

    @BeforeEach
    void resetCounters() {
        ListContract.reset();
        EditContract.reset();
        CommentsContract.reset();
        PromptContract.reset();
    }

    @Test
    void query_only_url_update_preserves_prompt_companion_runtime() {
        final ComponentSegment<RelativeUrl> root = renderAppOn("/items?p=1");

        assertEquals(1, ListContract.created);
        assertEquals(1, PromptContract.created);
        assertEquals(0, PromptContract.destroyed);

        emit(root, "stateUpdated.p", new ContextStateComponent.ContextValue.StringValue("2"));
        commands.drain(root);

        assertEquals(1, PromptContract.created,
                "query-only URL updates must not recreate stable companion contracts");
        assertEquals(0, PromptContract.destroyed,
                "query-only URL updates must not destroy the prompt/right-sidebar runtime");
    }

    @Test
    void inline_show_pushes_url_without_recreating_prompt_or_inline_runtime() {
        final ComponentSegment<RelativeUrl> root = renderAppOn("/items?p=1");

        assertEquals(1, ListContract.created);
        assertEquals(1, PromptContract.created);
        assertEquals(0, EditContract.created);

        emit(root, EventKeys.SHOW.name(),
                new ActionBindings.ShowPayload(EditContract.class, Map.of("id", "7")));
        commands.drain(root);

        assertEquals(1, ListContract.created,
                "inline SHOW starts from the existing routed list runtime");
        assertEquals(1, ListContract.destroyed,
                "inline SHOW replaces the list runtime once");
        assertEquals(1, EditContract.created,
                "inline SHOW must not create the edit runtime twice through a route rebuild");
        assertEquals(0, EditContract.destroyed,
                "the inline edit runtime should stay mounted after SHOW");
        assertEquals(1, PromptContract.created,
                "inline SHOW must preserve stable companion contracts");
        assertEquals(0, PromptContract.destroyed,
                "inline SHOW must not destroy the prompt/right-sidebar runtime");
        assertEquals(List.of("/items/7?p=1"), commands.pushHistoryTargets(),
                "inline SHOW should still reflect the form route in browser history");
    }

    @Test
    void inline_action_success_returns_without_recreating_prompt_or_restored_runtime() {
        final ComponentSegment<RelativeUrl> root = renderAppOn("/items?p=1");

        emit(root, EventKeys.SHOW.name(),
                new ActionBindings.ShowPayload(EditContract.class, Map.of("id", "7")));
        commands.drain(root);

        emit(root, EventKeys.ACTION_SUCCESS.name(),
                new EventKeys.ActionResult(EditContract.class));
        commands.drain(root);

        assertEquals(2, ListContract.created,
                "inline return should restore the list once, not rebuild it through routing");
        assertEquals(1, ListContract.destroyed,
                "the original list is destroyed only when the inline form replaces it");
        assertEquals(1, EditContract.created,
                "the inline edit runtime should be created once");
        assertEquals(1, EditContract.destroyed,
                "the inline edit runtime should be destroyed when returning to the list");
        assertEquals(1, PromptContract.created,
                "inline return must preserve stable companion contracts");
        assertEquals(0, PromptContract.destroyed,
                "inline return must not destroy the prompt/right-sidebar runtime");
        assertEquals(List.of("/items/7?p=1", "/items?p=1"), commands.pushHistoryTargets(),
                "inline SHOW and return should both be reflected in browser history");
        assertEquals(List.of(
                        AutoAddressBarSyncComponent.PathUpdateMode.PUSH_URL_ONLY,
                        AutoAddressBarSyncComponent.PathUpdateMode.PUSH_URL_ONLY),
                commands.pathUpdateModes(),
                "inline SHOW and inline return are scene-local transitions, not route rebuild requests");
    }

    @Test
    void page_change_after_inline_return_updates_restored_primary_url() {
        final ComponentSegment<RelativeUrl> root = renderAppOn("/items?p=1");

        emit(root, EventKeys.SHOW.name(),
                new ActionBindings.ShowPayload(EditContract.class, Map.of("id", "7")));
        commands.drain(root);
        emit(root, EventKeys.ACTION_SUCCESS.name(),
                new EventKeys.ActionResult(EditContract.class));
        commands.drain(root);
        emit(root, ListViewContract.PAGE_CHANGE_REQUESTED.name(), 2);
        commands.drain(root);

        assertEquals(2, ListContract.lastPage,
                "page changes after inline return should update the restored primary's scene-local query state");
        assertEquals(List.of("/items/7?p=1", "/items?p=1", "/items?p=2"),
                commands.pushHistoryTargets(),
                "page changes after inline return must build on the restored list URL");
        assertEquals(2, ListContract.created,
                "scene-local page changes after inline return should reuse the restored primary runtime");
        assertEquals(1, PromptContract.created,
                "scene-local page changes after inline return should preserve stable companion runtimes");
    }

    @Test
    void set_primary_switches_primary_without_recreating_prompt_or_new_runtime() {
        final ComponentSegment<RelativeUrl> root = renderAppOn("/items?p=1");

        emit(root, EventKeys.SET_PRIMARY.name(), CommentsContract.class);
        commands.drain(root);

        assertEquals(1, ListContract.created,
                "SET_PRIMARY starts from the existing routed list runtime");
        assertEquals(1, ListContract.destroyed,
                "SET_PRIMARY destroys the previous routed runtime once");
        assertEquals(1, CommentsContract.created,
                "SET_PRIMARY should create the new primary once, not recreate it through routing");
        assertEquals(0, CommentsContract.destroyed,
                "the selected primary should remain mounted after SET_PRIMARY");
        assertEquals(1, PromptContract.created,
                "SET_PRIMARY must preserve stable companion contracts");
        assertEquals(0, PromptContract.destroyed,
                "SET_PRIMARY must not destroy the prompt/right-sidebar runtime");
        assertEquals(List.of("/comments"), commands.pushHistoryTargets(),
                "SET_PRIMARY should still reflect the selected route in browser history");
        assertEquals(List.of(AutoAddressBarSyncComponent.PathUpdateMode.PUSH_URL_ONLY),
                commands.pathUpdateModes(),
                "SET_PRIMARY is a scene-local transition, not a route rebuild request");
    }

    @Test
    void set_primary_clears_stale_query_context_from_previous_primary() {
        final ComponentSegment<RelativeUrl> root = renderAppOn("/items?p=3");

        emit(root, EventKeys.SET_PRIMARY.name(), CommentsContract.class);
        commands.drain(root);

        assertEquals(1, CommentsContract.lastPage,
                "SET_PRIMARY to a different primary contract must not inherit the previous contract's page query");
        assertEquals("/comments", CommentsContract.lastRoutePath,
                "scene-local primary navigation must expose the selected route to downstream contracts");
        assertEquals(List.of("/comments"), commands.pushHistoryTargets(),
                "browser history should still reflect the selected primary route");
    }

    @Test
    void page_change_after_scene_local_set_primary_updates_selected_primary_url() {
        final ComponentSegment<RelativeUrl> root = renderAppOn("/items?p=3");

        emit(root, EventKeys.SET_PRIMARY.name(), CommentsContract.class);
        commands.drain(root);
        emit(root, ListViewContract.PAGE_CHANGE_REQUESTED.name(), 2);
        commands.drain(root);

        assertEquals(2, CommentsContract.lastPage,
                "page changes after SET_PRIMARY should update the selected primary's scene-local query state");
        assertEquals(List.of("/comments", "/comments?p=2"), commands.pushHistoryTargets(),
                "page changes after SET_PRIMARY must build on the selected primary URL, not the previous route");
        assertEquals(1, CommentsContract.created,
                "scene-local query updates should reuse the selected primary runtime");
        assertEquals(1, PromptContract.created,
                "scene-local query updates should preserve stable companion runtimes");
    }

    private ComponentSegment<RelativeUrl> renderAppOn(String url) {
        final UrlSyncComponent component = new UrlSyncComponent(parse(url));
        final ComponentContext context = new ComponentContext()
                .with(CommandsEnqueue.class, commands)
                .with(ContextKeys.APP_COMPOSITIONS, List.of(composition()));
        final TreeBuilder treeBuilder = new TreeBuilder(
                new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"),
                context,
                commands);

        final ComponentSegment<RelativeUrl> root = treeBuilder.openComponent(component);
        root.render(treeBuilder);
        treeBuilder.closeComponent();
        return root;
    }

    private Composition composition() {
        final Group group = new Group("Items")
                .bind(ListContract.class, ListContract::new, SceneComponentReuseTests::emptyView)
                .bind(EditContract.class, EditContract::new, SceneComponentReuseTests::emptyView)
                .bind(CommentsContract.class, CommentsContract::new, SceneComponentReuseTests::emptyView)
                .bind(PromptContract.class, PromptContract::new, SceneComponentReuseTests::emptyView);
        final Router router = new Router()
                .route("/items", ListContract.class)
                .route("/items/:id", EditContract.class)
                .route("/comments", CommentsContract.class);
        final DefaultLayout layout = new DefaultLayout()
                .placement(EditContract.class, Placement.INLINE.primary())
                .rightSidebar(PromptContract.class);
        return new Composition(router, layout, group);
    }

    private static StatelessComponent emptyView() {
        return new StatelessComponent((rsp.component.View<StatelessComponent.Unit>) _ -> div());
    }

    private static RelativeUrl parse(String url) {
        int queryStart = url.indexOf('?');
        int fragmentStart = url.indexOf('#');
        String path = queryStart >= 0
                ? url.substring(0, queryStart)
                : (fragmentStart >= 0 ? url.substring(0, fragmentStart) : url);
        Query query = Query.EMPTY;
        if (queryStart >= 0) {
            int queryEnd = fragmentStart >= 0 ? fragmentStart : url.length();
            query = Query.of(url.substring(queryStart + 1, queryEnd));
        }
        Fragment fragment = fragmentStart >= 0
                ? new Fragment(url.substring(fragmentStart + 1))
                : Fragment.EMPTY;
        return new RelativeUrl(Path.of(path), query, fragment);
    }

    private static void emit(ComponentSegment<?> root, String eventName, Object payload) {
        for (ComponentEventEntry entry : root.recursiveComponentEvents()) {
            if (entry.matches(eventName)) {
                entry.eventHandler().accept(new ComponentEventEntry.EventContext(eventName, payload));
            }
        }
    }

    static final class ListContract extends ListViewContract<Object> {
        private static final QueryParam<Integer> PAGE =
                new QueryParam<>("p", Integer.class, 1);
        static int created;
        static int destroyed;
        static int lastPage;

        ListContract(Lookup lookup) {
            super(lookup);
            created++;
        }

        static void reset() {
            created = 0;
            destroyed = 0;
            lastPage = 0;
        }

        @Override
        public ComponentContext enrichContext(ComponentContext context) {
            lastPage = page();
            return super.enrichContext(context);
        }

        @Override
        public String title() {
            return "Items";
        }

        @Override
        protected QueryParam<Integer> pageQueryParam() {
            return PAGE;
        }

        @Override
        public String sort() {
            return "";
        }

        @Override
        public List<Object> items() {
            return List.of();
        }

        @Override
        protected Class<? extends ViewContract> createElementContract() {
            return EditContract.class;
        }

        @Override
        protected Class<? extends ViewContract> editElementContract() {
            return EditContract.class;
        }

        @Override
        protected void onDestroy() {
            destroyed++;
            super.onDestroy();
        }
    }

    static final class EditContract extends ViewContract {
        static int created;
        static int destroyed;

        EditContract(Lookup lookup) {
            super(lookup);
            created++;
        }

        static void reset() {
            created = 0;
            destroyed = 0;
        }

        @Override
        public ComponentContext enrichContext(ComponentContext context) {
            return context;
        }

        @Override
        public String title() {
            return "Edit";
        }

        @Override
        protected void onDestroy() {
            destroyed++;
            super.onDestroy();
        }
    }

    static final class CommentsContract extends ListViewContract<Object> {
        private static final QueryParam<Integer> PAGE =
                new QueryParam<>("p", Integer.class, 1);
        static int created;
        static int destroyed;
        static int lastPage;
        static String lastRoutePath;

        CommentsContract(Lookup lookup) {
            super(lookup);
            created++;
        }

        static void reset() {
            created = 0;
            destroyed = 0;
            lastPage = 0;
            lastRoutePath = null;
        }

        @Override
        public ComponentContext enrichContext(ComponentContext context) {
            lastPage = page();
            lastRoutePath = lookup.get(ContextKeys.ROUTE_PATH);
            return super.enrichContext(context);
        }

        @Override
        public String title() {
            return "Comments";
        }

        @Override
        protected QueryParam<Integer> pageQueryParam() {
            return PAGE;
        }

        @Override
        public String sort() {
            return "";
        }

        @Override
        public List<Object> items() {
            return List.of();
        }

        @Override
        protected Class<? extends ViewContract> createElementContract() {
            return EditContract.class;
        }

        @Override
        protected Class<? extends ViewContract> editElementContract() {
            return EditContract.class;
        }

        @Override
        protected void onDestroy() {
            destroyed++;
            super.onDestroy();
        }
    }

    static final class PromptContract extends ViewContract {
        static int created;
        static int destroyed;

        PromptContract(Lookup lookup) {
            super(lookup);
            created++;
        }

        static void reset() {
            created = 0;
            destroyed = 0;
        }

        @Override
        public ComponentContext enrichContext(ComponentContext context) {
            return context;
        }

        @Override
        public String title() {
            return "Prompt";
        }

        @Override
        protected void onDestroy() {
            destroyed++;
            super.onDestroy();
        }
    }

    private static final class RecordingCommands implements CommandsEnqueue {
        private final List<Command> commands = new ArrayList<>();
        private int processed;

        @Override
        public void offer(Command command) {
            commands.add(command);
        }

        private void drain(ComponentSegment<?> root) {
            while (processed < commands.size()) {
                switch (commands.get(processed++)) {
                    case ComponentEventNotification notification ->
                            emit(root, notification.eventType(), notification.eventObject());
                    case GenericTaskEvent taskEvent -> taskEvent.task().run();
                    default -> {
                        // Remote commands are assertions here, not executable test work.
                    }
                }
            }
        }

        private List<String> pushHistoryTargets() {
            return commands.stream()
                    .filter(RemoteCommand.PushHistory.class::isInstance)
                    .map(RemoteCommand.PushHistory.class::cast)
                    .map(RemoteCommand.PushHistory::path)
                    .toList();
        }

        private List<AutoAddressBarSyncComponent.PathUpdateMode> pathUpdateModes() {
            return commands.stream()
                    .filter(ComponentEventNotification.class::isInstance)
                    .map(ComponentEventNotification.class::cast)
                    .filter(notification -> AutoAddressBarSyncComponent.SET_PATH.name()
                            .equals(notification.eventType()))
                    .map(ComponentEventNotification::eventObject)
                    .filter(AutoAddressBarSyncComponent.PathUpdate.class::isInstance)
                    .map(AutoAddressBarSyncComponent.PathUpdate.class::cast)
                    .map(AutoAddressBarSyncComponent.PathUpdate::mode)
                    .toList();
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
}
