package rsp.app.posts.services;

import org.junit.jupiter.api.Test;
import rsp.app.posts.entities.Comment;
import rsp.compositions.block.ListPage;
import rsp.compositions.block.ListQuery;
import rsp.compositions.block.SortDirection;
import rsp.compositions.block.SortSpec;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentServiceGridTests {

    @Test
    void filters_by_post_and_returns_exact_total() {
        CommentService service = new CommentService();
        ListQuery query = new ListQuery(1, 10, new SortSpec("text", SortDirection.ASC),
                "comment", Map.of("postId", "2"));

        ListPage<Comment> page = service.findAll(query);

        assertEquals(3, page.totalItems());
        page.items().forEach(comment -> assertEquals("2", comment.postId()));
    }

    @Test
    void sorts_by_numeric_post_id_descending() {
        CommentService service = new CommentService();
        ListQuery query = new ListQuery(1, 10, new SortSpec("postId", SortDirection.DESC), "", Map.of());

        assertEquals("5", service.findAll(query).items().getFirst().postId());
    }

    @Test
    void page_boundaries_and_partial_delete_results_are_exact() {
        CommentService service = new CommentService();
        ListQuery lastPage = new ListQuery(2, 10, new SortSpec("id", SortDirection.ASC), "", Map.of());

        ListPage<Comment> page = service.findAll(lastPage);
        var deleted = service.deleteAll(Set.of("15", "missing"));

        assertEquals(15, page.totalItems());
        assertEquals(5, page.items().size());
        assertEquals(Set.of("15"), deleted.deletedIds());
        assertEquals(Set.of("missing"), deleted.failedIds());
        assertTrue(service.find("15").isEmpty());
    }
}
