package rsp.app.posts.services;

import rsp.app.posts.entities.Comment;
import rsp.component.ContextKey;
import rsp.compositions.block.DeleteResult;
import rsp.compositions.block.FormMutationResult;
import rsp.compositions.block.ListPage;
import rsp.compositions.block.ListQuery;
import rsp.compositions.block.SortDirection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
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
    private final Predicate<String> postExists;

    public CommentService() {
        this(_ -> true);
    }

    public CommentService(Predicate<String> postExists) {
        this.postExists = Objects.requireNonNull(postExists, "postExists");
        // Pre-populate with stub data: 3 comments for first 5 posts
        for (int postId = 1; postId <= 5; postId++) {
            for (int c = 1; c <= 3; c++) {
                create(new Comment(null, "Comment " + c + " on post " + postId, String.valueOf(postId)));
            }
        }
    }

    public List<Comment> findAll(final int page, final int pageSize, final String sort) {
        return findAll(new ListQuery(Math.max(1, page), pageSize,
                new rsp.compositions.block.SortSpec("text",
                        SortDirection.parse(sort, SortDirection.ASC)), "", Map.of())).items();
    }

    public ListPage<Comment> findAll(final ListQuery query) {
        List<Comment> matching = comments.values().stream()
                .filter(comment -> matchesSearch(comment, query.search()))
                .filter(comment -> matchesFilters(comment, query.filters()))
                .sorted(comparator(query))
                .collect(Collectors.toList());
        long requestedOffset = (long) (query.page() - 1) * query.pageSize();
        int from = (int) Math.min(requestedOffset, matching.size());
        int to = Math.min(from + query.pageSize(), matching.size());
        return new ListPage<>(matching.subList(from, to), matching.size());
    }

    public Optional<Comment> find(final String id) {
        return Optional.ofNullable(comments.get(id));
    }

    public String create(final Comment comment) {
        FormMutationResult result = createResult(comment);
        if (!result.succeeded()) throw new IllegalArgumentException(result.message());
        return result.entityId();
    }

    public FormMutationResult createResult(final Comment comment) {
        FormMutationResult invalid = validate(comment);
        if (invalid != null) return invalid;
        String id = String.valueOf(idGenerator.getAndIncrement());
        Comment newComment = new Comment(id, comment.text(), comment.postId());
        comments.put(id, newComment);
        return FormMutationResult.saved(id, "Comment created.");
    }

    public boolean update(final String id, final Comment comment) {
        return updateResult(id, comment).succeeded();
    }

    public FormMutationResult updateResult(final String id, final Comment comment) {
        if (id == null || !comments.containsKey(id)) {
            return FormMutationResult.notFound("The comment no longer exists.");
        }
        FormMutationResult invalid = validate(comment);
        if (invalid != null) return invalid;
        comments.put(id, new Comment(id, comment.text(), comment.postId()));
        return FormMutationResult.saved(id, "Comment updated.");
    }

    public boolean delete(final String id) {
        return comments.remove(id) != null;
    }

    public FormMutationResult deleteResult(final String id) {
        return delete(id)
                ? FormMutationResult.saved(id, "Comment deleted.")
                : FormMutationResult.notFound("The comment no longer exists.");
    }

    /** Delete comments owned by a deleted post; used by the demo's cascade policy. */
    public int deleteByPostId(String postId) {
        int deleted = 0;
        for (Comment comment : List.copyOf(comments.values())) {
            if (Objects.equals(postId, comment.postId()) && delete(comment.id())) deleted++;
        }
        return deleted;
    }

    /**
     * Delete multiple comments by their IDs.
     *
     * @param ids Set of comment IDs to delete
     * @return Number of comments successfully deleted
     */
    public int bulkDelete(final Set<String> ids) {
        return deleteAll(ids).deletedIds().size();
    }

    public DeleteResult deleteAll(final Set<String> ids) {
        Set<String> deleted = new LinkedHashSet<>();
        Set<String> failed = new LinkedHashSet<>();
        for (String id : ids) {
            if (comments.remove(id) != null) {
                deleted.add(id);
            } else {
                failed.add(id);
            }
        }
        return new DeleteResult(deleted, failed);
    }

    private Comparator<Comment> comparator(ListQuery query) {
        String field = query.sort() == null ? "text" : query.sort().field();
        Comparator<Comment> comparator = switch (field) {
            case "id" -> Comparator.comparingInt(comment -> numericId(comment.id()));
            case "text" -> Comparator.comparing(Comment::text, String.CASE_INSENSITIVE_ORDER);
            case "postId" -> Comparator.comparingInt(comment -> numericId(comment.postId()));
            default -> throw new IllegalArgumentException("Unsupported comment sort field: " + field);
        };
        if (query.sort() != null && query.sort().direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(Comment::id, String.CASE_INSENSITIVE_ORDER);
    }

    private static boolean matchesSearch(Comment comment, String search) {
        if (search == null || search.isBlank()) return true;
        String needle = search.toLowerCase(Locale.ROOT);
        return comment.id().toLowerCase(Locale.ROOT).contains(needle)
                || comment.text().toLowerCase(Locale.ROOT).contains(needle)
                || comment.postId().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean matchesFilters(Comment comment, Map<String, String> filters) {
        return contains(comment.text(), filters.get("text"))
                && contains(comment.postId(), filters.get("postId"));
    }

    private static boolean contains(String value, String filter) {
        return filter == null || filter.isBlank()
                || value.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    private static int numericId(String id) {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private FormMutationResult validate(Comment comment) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        if (comment == null) return FormMutationResult.failure("Comment data is required.");
        if (comment.text() == null || comment.text().isBlank()) {
            errors.put("text", List.of("Comment is required"));
        } else if (comment.text().length() > 1_000) {
            errors.put("text", List.of("Comment must be at most 1000 characters"));
        }
        String postId = comment.postId();
        if (postId == null || postId.isBlank()) {
            errors.put("postId", List.of("Post ID is required"));
        } else if (!postId.matches("[1-9][0-9]*")) {
            errors.put("postId", List.of("Post ID must be a positive integer"));
        } else if (!postExists.test(postId)) {
            errors.put("postId", List.of("Post " + postId + " does not exist"));
        }
        return errors.isEmpty() ? null : FormMutationResult.invalid(errors);
    }
}
