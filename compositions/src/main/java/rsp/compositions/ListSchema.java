package rsp.compositions;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema metadata for list views.
 * <p>
 * Provides column definitions that UI components use to render data adaptively.
 * Schema flows alongside data through ComponentContext, enabling UI components
 * to render any number of columns of any types.
 */
public record ListSchema(List<ColumnDef> columns) {

    /**
     * Column definition with name, display name, and type information.
     */
    public record ColumnDef(String name, String displayName, Class<?> type) {
        public ColumnDef(String name, Class<?> type) {
            this(name, formatDisplayName(name), type);
        }

        private static String formatDisplayName(String name) {
            // Convert camelCase to Title Case
            return name.substring(0, 1).toUpperCase() +
                   name.substring(1).replaceAll("([A-Z])", " $1");
        }
    }

    /**
     * Extract schema from a Record class using reflection.
     *
     * @param recordClass The Record class to extract schema from
     * @return ListSchema with columns matching Record components
     */
    public static ListSchema fromRecordClass(Class<?> recordClass) {
        if (!recordClass.isRecord()) {
            throw new IllegalArgumentException("Class must be a Record type: " + recordClass.getName());
        }

        RecordComponent[] components = recordClass.getRecordComponents();
        List<ColumnDef> columns = new ArrayList<>();

        for (RecordComponent component : components) {
            columns.add(new ColumnDef(
                component.getName(),
                component.getType()
            ));
        }

        return new ListSchema(columns);
    }

    /**
     * Extract schema from the first item in a list by inspecting the actual object.
     * Works for Record types.
     *
     * @param item The first item to inspect
     * @return ListSchema derived from the item's type
     */
    public static ListSchema fromFirstItem(Object item) {
        if (item == null) {
            return new ListSchema(List.of());
        }

        return fromRecordClass(item.getClass());
    }

    /**
     * Convert a domain object to a Map representation based on this schema.
     *
     * @param item The domain object (must be a Record)
     * @return Map with column names as keys and field values
     */
    public Map<String, Object> toMap(Object item) {
        if (item == null) {
            return Map.of();
        }

        if (!item.getClass().isRecord()) {
            throw new IllegalArgumentException("Item must be a Record type: " + item.getClass().getName());
        }

        Map<String, Object> map = new LinkedHashMap<>();
        RecordComponent[] components = item.getClass().getRecordComponents();

        for (RecordComponent component : components) {
            try {
                String name = component.getName();
                Object value = component.getAccessor().invoke(item);
                map.put(name, value);
            } catch (Exception e) {
                throw new RuntimeException("Failed to extract field: " + component.getName(), e);
            }
        }

        return map;
    }

    /**
     * Convert a list of domain objects to Map representations.
     *
     * @param items List of domain objects
     * @return List of Maps with column data
     */
    public List<Map<String, Object>> toMapList(List<?> items) {
        return items.stream()
            .map(this::toMap)
            .toList();
    }

    /**
     * Customize this schema by renaming a column's display name.
     */
    public ListSchema renameColumn(String columnName, String newDisplayName) {
        List<ColumnDef> newColumns = columns.stream()
            .map(col -> col.name().equals(columnName)
                ? new ColumnDef(col.name(), newDisplayName, col.type())
                : col)
            .toList();
        return new ListSchema(newColumns);
    }

    /**
     * Customize this schema by hiding a column.
     */
    public ListSchema hideColumn(String columnName) {
        List<ColumnDef> newColumns = columns.stream()
            .filter(col -> !col.name().equals(columnName))
            .toList();
        return new ListSchema(newColumns);
    }

    /**
     * Customize this schema by reordering columns.
     */
    public ListSchema reorderColumns(String... orderedNames) {
        Map<String, ColumnDef> columnMap = new LinkedHashMap<>();
        for (ColumnDef col : columns) {
            columnMap.put(col.name(), col);
        }

        List<ColumnDef> newColumns = new ArrayList<>();
        for (String name : orderedNames) {
            ColumnDef col = columnMap.get(name);
            if (col != null) {
                newColumns.add(col);
            }
        }

        return new ListSchema(newColumns);
    }
}
