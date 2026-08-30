package rsp.compositions.block;

import java.util.List;

/**
 * One result page and the exact total number of items matching the query before
 * pagination.
 *
 * @param items immutable rows on the requested page
 * @param totalItems exact matching total
 */
public record ListPage<T>(List<T> items, long totalItems) {
    public ListPage {
        items = items == null ? List.of() : List.copyOf(items);
        if (totalItems < 0) {
            throw new IllegalArgumentException("totalItems cannot be negative");
        }
        if (items.size() > totalItems) {
            throw new IllegalArgumentException("a page cannot contain more items than totalItems");
        }
    }

    /** Calculate the number of pages for a positive page size. */
    public int totalPages(int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be at least 1");
        }
        if (totalItems == 0) return 0;
        long pages = 1 + (totalItems - 1) / pageSize;
        return (int) Math.min(pages, Integer.MAX_VALUE);
    }
}
