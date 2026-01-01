package rsp.compositions;

import rsp.component.ComponentStateSupplier;
import rsp.component.definitions.Component;

import java.util.List;

public abstract class ListView extends Component<List<String>> {
    @Override
    public ComponentStateSupplier<List<String>> initStateSupplier() {
        return (_, context) -> {
            // Read items from context (populated by ServicesComponent)
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) context.getAttribute("list.items");

            if (items == null) {
                return List.of();
            }

            // Convert domain objects to CSV strings for UI rendering
            return items.stream()
                .map(this::convertToCsv)
                .toList();
        };
    }

    /**
     * Override this method to customize CSV conversion for domain objects.
     * Default implementation uses toString() which works for Record types.
     */
    protected String convertToCsv(Object item) {
        return item.toString();
    }

    static final class ListViewState { // the common typed language for state
        public String schema;
        public String data;
    }

}
