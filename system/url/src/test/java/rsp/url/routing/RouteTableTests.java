package rsp.url.routing;

import org.junit.jupiter.api.Test;
import rsp.url.Path;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTableTests {
    @Test
    void extracts_named_parameters_and_expands_with_encoding() {
        RouteTemplate template = RouteTemplate.parse("/posts/{postId}/comments/{commentId}");

        PathMatch match = template.match(Path.parse("/posts/a%20b/comments/c%2Bd")).orElseThrow();

        assertEquals("a b", match.required("postId"));
        assertEquals("c+d", match.required("commentId"));
        assertEquals("/posts/a%20b/comments/c+d",
                template.expand(Map.of("postId", "a b", "commentId", "c+d")));
    }

    @Test
    void literals_win_independently_of_registration_order() {
        RouteTable<String> routes = RouteTable.<String>builder()
                .route("/posts/{id}", "item")
                .route("/posts/new", "new")
                .build();

        assertEquals("new", routes.match(Path.of("/posts/new")).orElseThrow().target());
        assertEquals("item", routes.match(Path.of("/posts/42")).orElseThrow().target());
    }

    @Test
    void rejects_overlapping_templates_with_equal_specificity() {
        RouteTable.Builder<String> routes = RouteTable.<String>builder()
                .route("/{entity}/me", "first")
                .route("/books/{id}", "second");

        assertThrows(IllegalArgumentException.class, routes::build);
    }

    @Test
    void supports_reverse_and_parent_lookup() {
        RouteTable<String> routes = RouteTable.<String>builder()
                .route("/posts", "list")
                .route("/posts/{id}", "edit")
                .build();

        RouteTemplate edit = routes.templateFor("edit").orElseThrow();

        assertEquals("/posts/{id}", edit.toString());
        assertEquals("list", routes.parentOf(edit).orElseThrow().target());
    }

    @Test
    void validates_template_shape() {
        assertThrows(IllegalArgumentException.class, () -> RouteTemplate.parse("relative/{id}"));
        assertThrows(IllegalArgumentException.class, () -> RouteTemplate.parse("/{id}/{id}"));
        assertThrows(IllegalArgumentException.class, () -> RouteTemplate.parse("/post-{id}"));
        assertTrue(RouteTemplate.parse("/").parameterNames().isEmpty());
    }
}
