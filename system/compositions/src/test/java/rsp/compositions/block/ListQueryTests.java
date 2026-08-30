package rsp.compositions.block;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ListQueryTests {

    @Test
    void query_validates_page_and_page_size() {
        assertThrows(IllegalArgumentException.class,
                () -> new ListQuery(0, 10, null, "", Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ListQuery(1, 0, null, "", Map.of()));
    }

    @Test
    void query_normalizes_search_and_filters() {
        ListQuery query = new ListQuery(2, 25, new SortSpec("title", SortDirection.DESC),
                "  needle  ", Map.of("title", "  draft ", "empty", ""));

        assertEquals("needle", query.search());
        assertEquals(Map.of("title", "draft"), query.filters());
        assertThrows(UnsupportedOperationException.class, () -> query.filters().put("x", "y"));
    }

    @Test
    void criteria_sort_and_page_size_reset_page() {
        ListQuery query = new ListQuery(4, 10, new SortSpec("title", SortDirection.ASC), "", Map.of());

        assertEquals(1, query.withSort(new SortSpec("id", SortDirection.DESC)).page());
        assertEquals(1, query.withCriteria("x", Map.of()).page());
        assertEquals(1, query.withPageSize(25).page());
        assertEquals(3, query.withPage(3).page());
    }

    @Test
    void page_calculates_total_pages() {
        ListPage<String> page = new ListPage<>(java.util.List.of("one"), 21);

        assertEquals(3, page.totalPages(10));
        assertThrows(IllegalArgumentException.class, () -> page.totalPages(0));
    }

    @Test
    void page_count_saturates_without_overflow() {
        ListPage<String> page = new ListPage<>(java.util.List.of(), Long.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, page.totalPages(1));
    }

    @Test
    void delete_result_rejects_overlapping_outcomes() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeleteResult(Set.of("1"), Set.of("1")));
    }
}
