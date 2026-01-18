package rsp.compositions;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.util.List;
import java.util.Map;

/**
 * ListView - Adaptive list view component.
 * <p>
 * Renders lists of any domain objects based on schema metadata.
 * UI adapts to any number of columns and types at runtime.
 */
public abstract class ListView extends Component<ListView.ListViewState> {

    protected Lookup lookup;

    /**
     * State containing both data, schema, and pagination/sorting info for adaptive rendering.
     *
     * @param rows The list data as maps
     * @param schema The schema for column definitions
     * @param page Current page number
     * @param sort Current sort direction
     * @param modulePath Base path for this module (e.g., "/posts")
     * @param editMode The edit/create mode (SEPARATE_PAGE, QUERY_PARAM, MODAL)
     * @param createToken The token used for create mode URLs (e.g., "new")
     */
    public record ListViewState(List<Map<String, Object>> rows, DataSchema schema, int page, String sort,
                                 String modulePath, EditMode editMode, String createToken) {
        public ListViewState {
            rows = rows != null ? rows : List.of();
            schema = schema != null ? schema : new DataSchema(List.of());
            if (page < 1) page = 1;
            sort = sort != null ? sort : "asc";
            modulePath = modulePath != null ? modulePath : "/";
            editMode = editMode != null ? editMode : EditMode.SEPARATE_PAGE;
            createToken = createToken != null ? createToken : "new";
        }

        /**
         * Backwards-compatible constructor without editMode and createToken.
         */
        public ListViewState(List<Map<String, Object>> rows, DataSchema schema, int page, String sort, String modulePath) {
            this(rows, schema, page, sort, modulePath, EditMode.SEPARATE_PAGE, "new");
        }
    }

    @Override
    public ComponentStateSupplier<ListViewState> initStateSupplier() {
        return (_, context) -> {
            // Read items, schema, page, and sort from context (populated by ServicesComponent)
            List<?> items = (List<?>) context.get(ContextKeys.LIST_ITEMS);
            DataSchema schema = context.get(ContextKeys.LIST_SCHEMA);
            Integer page = context.get(ContextKeys.LIST_PAGE);
            String sort = context.get(ContextKeys.LIST_SORT);
            EditMode editMode = context.get(ContextKeys.EDIT_MODE);
            String createToken = context.get(ContextKeys.CREATE_TOKEN);

            if (page == null) page = 1;
            if (sort == null) sort = "asc";
            if (editMode == null) editMode = EditMode.SEPARATE_PAGE;
            if (createToken == null) createToken = "new";

            // Derive module path from current route
            String modulePath = deriveModulePath(context);

            if (items == null || items.isEmpty()) {
                return new ListViewState(List.of(), schema, page, sort, modulePath, editMode, createToken);
            }

            // Convert domain objects to Map representation using schema
            List<Map<String, Object>> rows = schema.toMapList(items);

            return new ListViewState(rows, schema, page, sort, modulePath, editMode, createToken);
        };
    }

    /**
     * Derive module base path from current route.
     * <p>
     * Reads route.path and strips query params.
     * Example: "/posts?page=3" → "/posts"
     */
    private String deriveModulePath(ComponentContext context) {
        String routePath = context.get(ContextKeys.ROUTE_PATH);
        if (routePath == null) {
            return "/";
        }

        // Strip query params if present
        int queryStart = routePath.indexOf('?');
        if (queryStart != -1) {
            routePath = routePath.substring(0, queryStart);
        }

        return routePath;
    }

    @Override
    public ComponentSegment<ListViewState> createComponentSegment(final QualifiedSessionId sessionId,
                                                                    final TreePositionPath componentPath,
                                                                    final TreeBuilderFactory treeBuilderFactory,
                                                                    final ComponentContext componentContext,
                                                                    final CommandsEnqueue commandsEnqueue) {
        // Create Lookup for use in view (for event publishing)
        this.lookup = createLookup(componentContext, commandsEnqueue);
        return super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
    }

    /**
     * Create a Lookup from ComponentContext for event publishing.
     */
    private Lookup createLookup(ComponentContext context, CommandsEnqueue commandsEnqueue) {
        Subscriber subscriber = context.get(Subscriber.class);
        if (subscriber == null) {
            // Fallback: create a no-op subscriber for publish-only usage
            subscriber = NoOpSubscriber.INSTANCE;
        }
        return new ContextLookup(context, commandsEnqueue, subscriber);
    }

    /**
     * No-op Subscriber for Views that only need to publish events, not subscribe.
     */
    private static final class NoOpSubscriber implements Subscriber {
        static final NoOpSubscriber INSTANCE = new NoOpSubscriber();

        @Override
        public void addWindowEventHandler(String eventType, java.util.function.Consumer<rsp.page.EventContext> eventHandler,
                                          boolean preventDefault, rsp.dom.DomEventEntry.Modifier modifier) {}

        @Override
        public void addComponentEventHandler(String eventType, java.util.function.Consumer<ComponentEventEntry.EventContext> eventHandler,
                                             boolean preventDefault) {}
    }
}
