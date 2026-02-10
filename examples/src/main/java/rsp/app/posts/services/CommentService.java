package rsp.app.posts.services;

import rsp.app.posts.entities.Comment;
import rsp.component.ContextKey;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CommentService {

    /**
     * Comment service for CRUD operations on comments.
     * Stored as: CommentService.class → CommentService instance
     */
    public static final ContextKey.ClassKey<CommentService> COMMENT_SERVICE =
            new ContextKey.ClassKey<>(CommentService.class);

    private final Map<String, Comment> comments = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    public CommentService() {
        // Pre-populate with stub data: 3 comments for first 5 posts
        for (int postId = 1; postId <= 5; postId++) {
            for (int c = 1; c <= 3; c++) {
                create(new Comment(null, "Comment " + c + " on post " + postId, String.valueOf(postId)));
            }
        }
    }

    public List<Comment> findAll(final int page, final int pageSize, final String sort) {
        Comparator<Comment> comparator = Comparator.comparing(Comment::text);
        if ("desc".equalsIgnoreCase(sort)) {
            comparator = comparator.reversed();
        }

        return comments.values().stream()
                .sorted(comparator)
                .skip((long) (page - 1) * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList());
    }

    public Optional<Comment> find(final String id) {
        return Optional.ofNullable(comments.get(id));
    }

    public String create(final Comment comment) {
        String id = String.valueOf(idGenerator.getAndIncrement());
        Comment newComment = new Comment(id, comment.text(), comment.postId());
        comments.put(id, newComment);
        return id;
    }

    public boolean update(final String id, final Comment comment) {
        if (comments.containsKey(id)) {
            comments.put(id, new Comment(id, comment.text(), comment.postId()));
            return true;
        }
        return false;
    }

    public boolean delete(final String id) {
        return comments.remove(id) != null;
    }

    /**
     * Delete multiple comments by their IDs.
     *
     * @param ids Set of comment IDs to delete
     * @return Number of comments successfully deleted
     */
    public int bulkDelete(final Set<String> ids) {
        int deleted = 0;
        for (String id : ids) {
            if (comments.remove(id) != null) {
                deleted++;
            }
        }
        return deleted;
    }
}
