package rsp.compositions.block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validated query state for a schema-backed list.
 *
 * @param page one-based page number
 * @param pageSize requested rows per page
 * @param sort optional single-column sort
 * @param search trimmed global search text
 * @param filters trimmed, nonblank filters keyed by schema field name
 */
public record ListQuery(int page,
                        int pageSize,
                        SortSpec sort,
                        String search,
                        Map<String, String> filters) {
    public ListQuery {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be at least 1");
        }
        search = search == null ? "" : search.trim();
        filters = filters == null ? Map.of() : normalizedFilters(filters);
    }

    /** Return this query on another page. */
    public ListQuery withPage(int value) {
        return new ListQuery(value, pageSize, sort, search, filters);
    }

    /** Return this query with a new page size and reset it to page one. */
    public ListQuery withPageSize(int value) {
        return new ListQuery(1, value, sort, search, filters);
    }

    /** Return this query with a new sort and reset it to page one. */
    public ListQuery withSort(SortSpec value) {
        return new ListQuery(1, pageSize, value, search, filters);
    }

    /** Return this query with new search/filter criteria and reset it to page one. */
    public ListQuery withCriteria(String newSearch, Map<String, String> newFilters) {
        return new ListQuery(1, pageSize, sort, newSearch, newFilters);
    }

    private static Map<String, String> normalizedFilters(Map<String, String> source) {
        Map<String, String> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                normalized.put(key, value.trim());
            }
        });
        return Collections.unmodifiableMap(normalized);
    }
}
