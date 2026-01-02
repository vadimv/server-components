package rsp.compositions;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ListView - Adaptive list view component.
 * <p>
 * Renders lists of any domain objects based on schema metadata.
 * UI adapts to any number of columns and types at runtime.
 */
public abstract class ListView extends Component<ListView.ListViewState> {

    protected Consumer<Command> commandsEnqueue;

    /**
     * State containing both data, schema, and pagination/sorting info for adaptive rendering.
     */
    public record ListViewState(List<Map<String, Object>> rows, ListSchema schema, int page, String sort) {
        public ListViewState {
            rows = rows != null ? rows : List.of();
            schema = schema != null ? schema : new ListSchema(List.of());
            if (page < 1) page = 1;
            sort = sort != null ? sort : "asc";
        }
    }

    @Override
    public ComponentStateSupplier<ListViewState> initStateSupplier() {
        return (_, context) -> {
            // Read items, schema, page, and sort from context (populated by ServicesComponent)
            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) context.getAttribute("list.items");
            ListSchema schema = (ListSchema) context.getAttribute("list.schema");
            Integer page = (Integer) context.getAttribute("list.page");
            String sort = (String) context.getAttribute("list.sort");

            if (page == null) page = 1;
            if (sort == null) sort = "asc";

            if (items == null || items.isEmpty()) {
                return new ListViewState(List.of(), schema, page, sort);
            }

            // Convert domain objects to Map representation using schema
            List<Map<String, Object>> rows = schema.toMapList(items);

            return new ListViewState(rows, schema, page, sort);
        };
    }

    @Override
    public ComponentSegment<ListViewState> createComponentSegment(final QualifiedSessionId sessionId,
                                                                    final TreePositionPath componentPath,
                                                                    final TreeBuilderFactory treeBuilderFactory,
                                                                    final ComponentContext componentContext,
                                                                    final Consumer<Command> commandsEnqueue) {
        // Store commandsEnqueue for use in view
        this.commandsEnqueue = commandsEnqueue;
        return super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
    }
}
