package rsp.app.posts.services;

import org.junit.jupiter.api.Test;
import rsp.app.posts.entities.Comment;
import rsp.app.posts.entities.Post;
import rsp.compositions.block.FormMutationResult;

import static org.junit.jupiter.api.Assertions.*;

class FormPersistenceTests {

    @Test
    void post_mutations_return_field_errors_and_not_found_outcomes() {
        PostService posts = new PostService();

        FormMutationResult blank = posts.createResult(new Post(null, "  ", "content"));
        FormMutationResult tooLong = posts.createResult(new Post(null, "x".repeat(201), "content"));
        FormMutationResult missing = posts.updateResult("missing", new Post(null, "Valid", "content"));

        assertEquals(FormMutationResult.Status.INVALID, blank.status());
        assertFalse(blank.fieldErrors().get("title").isEmpty());
        assertEquals(FormMutationResult.Status.INVALID, tooLong.status());
        assertEquals(FormMutationResult.Status.NOT_FOUND, missing.status());
    }

    @Test
    void comment_mutations_enforce_the_post_relationship() {
        PostService posts = new PostService();
        CommentService comments = new CommentService(posts::exists);

        FormMutationResult missingPost = comments.createResult(
                new Comment(null, "Orphan comment", "999999"));
        FormMutationResult malformedPost = comments.createResult(
                new Comment(null, "Malformed relationship", "not-an-id"));
        FormMutationResult valid = comments.createResult(
                new Comment(null, "Related comment", "1"));

        assertEquals(FormMutationResult.Status.INVALID, missingPost.status());
        assertTrue(missingPost.fieldErrors().containsKey("postId"));
        assertEquals(FormMutationResult.Status.INVALID, malformedPost.status());
        assertTrue(valid.succeeded());
        assertEquals("1", comments.find(valid.entityId()).orElseThrow().postId());
    }

    @Test
    void deleting_a_post_cascades_to_its_comments_in_the_demo_policy() {
        PostService posts = new PostService();
        CommentService comments = new CommentService(posts::exists);
        posts.onDelete(comments::deleteByPostId);
        String postId = posts.create(new Post(null, "Parent", "content"));
        String commentId = comments.create(new Comment(null, "Child", postId));

        FormMutationResult result = posts.deleteResult(postId);

        assertTrue(result.succeeded());
        assertTrue(comments.find(commentId).isEmpty());
    }

    @Test
    void comment_update_and_delete_report_missing_entities() {
        PostService posts = new PostService();
        CommentService comments = new CommentService(posts::exists);

        FormMutationResult update = comments.updateResult(
                "missing", new Comment(null, "Valid", "1"));
        FormMutationResult delete = comments.deleteResult("missing");

        assertEquals(FormMutationResult.Status.NOT_FOUND, update.status());
        assertEquals(FormMutationResult.Status.NOT_FOUND, delete.status());
    }
}
