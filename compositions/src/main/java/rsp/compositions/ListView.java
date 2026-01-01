package rsp.compositions;

import rsp.component.ComponentStateSupplier;
import rsp.component.definitions.Component;

import java.util.List;
import java.util.Map;

/**
 * ListView - Adaptive list view component.
 * <p>
 * Renders lists of any domain objects based on schema metadata.
 * UI adapts to any number of columns and types at runtime.
 */
public abstract class ListView extends Component<ListView.ListViewState> {

    /**
     * State containing both data and schema for adaptive rendering.
     */
    public record ListViewState(List<Map<String, Object>> rows, ListSchema schema) {
        public ListViewState {
            rows = rows != null ? rows : List.of();
            schema = schema != null ? schema : new ListSchema(List.of());
        }
    }

    @Override
    public ComponentStateSupplier<ListViewState> initStateSupplier() {
        return (_, context) -> {
            // Read items and schema from context (populated by ServicesComponent)
            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) context.getAttribute("list.items");
            ListSchema schema = (ListSchema) context.getAttribute("list.schema");

            if (items == null || items.isEmpty()) {
                return new ListViewState(List.of(), schema);
            }

            // Convert domain objects to Map representation using schema
            List<Map<String, Object>> rows = schema.toMapList(items);

            return new ListViewState(rows, schema);
        };
    }
}
