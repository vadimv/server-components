package rsp.app.posts.services;

import org.junit.jupiter.api.Test;
import rsp.app.posts.entities.Post;
import rsp.compositions.block.ListPage;
import rsp.compositions.block.ListQuery;
import rsp.compositions.block.SortDirection;
import rsp.compositions.block.SortSpec;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PostServiceGridTests {

    @Test
    void returns_exact_totals_and_bounded_pages() {
        PostService service = new PostService();

        ListPage<Post> page = service.findAll(query(3, 10, "id", SortDirection.ASC, "", Map.of()));

        assertEquals(25, page.totalItems());
        assertEquals(5, page.items().size());
        assertEquals("21", page.items().getFirst().id());
        assertEquals(3, page.totalPages(10));
    }

    @Test
    void sorts_by_requested_field_and_direction() {
        PostService service = new PostService();

        ListPage<Post> page = service.findAll(query(1, 10, "id", SortDirection.DESC, "", Map.of()));

        assertEquals("25", page.items().getFirst().id());
    }

    @Test
    void combines_search_and_column_filters() {
        PostService service = new PostService();

        ListPage<Post> page = service.findAll(query(1, 10, "title", SortDirection.ASC,
                "lorem", Map.of("title", "Title 25")));

        assertEquals(1, page.totalItems());
        assertEquals("Post Title 25", page.items().getFirst().title());
    }

    @Test
    void rejects_unknown_sort_fields() {
        PostService service = new PostService();

        assertThrows(IllegalArgumentException.class,
                () -> service.findAll(query(1, 10, "unknown", SortDirection.ASC, "", Map.of())));
    }

    @Test
    void very_large_page_is_empty_without_offset_overflow() {
        PostService service = new PostService();

        ListPage<Post> page = service.findAll(
                query(Integer.MAX_VALUE, 100, "id", SortDirection.ASC, "", Map.of()));

        assertTrue(page.items().isEmpty());
        assertEquals(25, page.totalItems());
    }

    @Test
    void deletion_reports_successful_and_missing_ids_separately() {
        PostService service = new PostService();

        var result = service.deleteAll(Set.of("1", "missing"));

        assertEquals(Set.of("1"), result.deletedIds());
        assertEquals(Set.of("missing"), result.failedIds());
        assertEquals(24, service.findAll(query(1, 100, "id", SortDirection.ASC, "", Map.of())).totalItems());
    }

    @Test
    void selection_items_are_complete_and_deterministically_sorted_by_label() {
        PostService service = new PostService();

        var choices = service.findAllForSelection();

        assertEquals(25, choices.size());
        assertEquals(25, choices.stream().map(Post::id).distinct().count());
        for (int index = 1; index < choices.size(); index++) {
            assertTrue(String.CASE_INSENSITIVE_ORDER.compare(
                    choices.get(index - 1).title(), choices.get(index).title()) <= 0);
        }
    }

    private static ListQuery query(int page, int size, String field, SortDirection direction,
                                   String search, Map<String, String> filters) {
        return new ListQuery(page, size, new SortSpec(field, direction), search, filters);
    }
}
