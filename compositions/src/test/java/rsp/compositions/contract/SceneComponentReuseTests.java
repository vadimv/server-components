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
import rsp.compositions.routing.Router;
import rsp.compositions.routing.UrlSyncComponent;
import rsp.dom.DomEventEntry;
import rsp.dom.TreePositionPath;
import rsp.page.EventContext;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.GenericTaskEvent;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;

import java.util.ArrayList;
import java.util.List;
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
        PromptContract.reset();
    }

    @Test
    void query_only_url_update_preserves_prompt_companion_runtime() {
        final ComponentSegment<RelativeUrl> root = renderAppOn("/items?p=1");

        assertEquals(1, ListContract.created);
        assertEquals(1, PromptContract.created);
        assertEquals(0, PromptContract.destroyed);

        emit(root, "stateUpdated.p", new ContextStateComponent.ContextValue.StringValue("2"));
        commands.runTasks();

        assertEquals(1, PromptContract.created,
                "query-only URL updates must not recreate stable companion contracts");
        assertEquals(0, PromptContract.destroyed,
                "query-only URL updates must not destroy the prompt/right-sidebar runtime");
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
                .bind(PromptContract.class, PromptContract::new, SceneComponentReuseTests::emptyView);
        final Router router = new Router()
                .route("/items", ListContract.class);
        final DefaultLayout layout = new DefaultLayout()
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

    static final class ListContract extends ViewContract {
        static int created;
        static int destroyed;

        ListContract(Lookup lookup) {
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
            return "Items";
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

        @Override
        public void offer(Command command) {
            commands.add(command);
        }

        private void runTasks() {
            for (int i = 0; i < commands.size(); i++) {
                if (commands.get(i) instanceof GenericTaskEvent taskEvent) {
                    taskEvent.task().run();
                }
            }
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
