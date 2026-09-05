package rsp.app.posts.services;

import rsp.app.posts.entities.Post;
import rsp.component.ContextKey;
import rsp.compositions.block.DeleteResult;
import rsp.compositions.block.FormMutationResult;
import rsp.compositions.block.ListPage;
import rsp.compositions.block.ListQuery;
import rsp.compositions.block.SortDirection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PostService {

    /**
     * Post service for CRUD operations on posts.
     * Stored as: PostService.class → PostService instance
     */
    public static final ContextKey.ClassKey<PostService> POST_SERVICE =
            new ContextKey.ClassKey<>(PostService.class);


    private final Map<String, Post> posts = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);
    private volatile Consumer<String> deleteListener = _ -> { };

    public PostService() {
        // Pre-populate with dummy data
        for (int i = 1; i <= 25; i++) {
            create(new Post(null, "Post Title " + i, "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " + i));
        }
    }

    public List<Post> findAll(final int page, final int pageSize, final String sort) {
        return findAll(new ListQuery(Math.max(1, page), pageSize,
                new rsp.compositions.block.SortSpec("title",
                        SortDirection.parse(sort, SortDirection.ASC)), "", Map.of())).items();
    }

    public ListPage<Post> findAll(final ListQuery query) {
        Comparator<Post> comparator = comparator(query);
        List<Post> matching = posts.values().stream()
                .filter(post -> matchesSearch(post, query.search()))
                .filter(post -> matchesFilters(post, query.filters()))
                .sorted(comparator)
                .collect(Collectors.toList());
        long requestedOffset = (long) (query.page() - 1) * query.pageSize();
        int from = (int) Math.min(requestedOffset, matching.size());
        int to = Math.min(from + query.pageSize(), matching.size());
        return new ListPage<>(matching.subList(from, to), matching.size());
    }

    public Optional<Post> find(final String id) {
        return Optional.ofNullable(posts.get(id));
    }

    public boolean exists(final String id) {
        return id != null && posts.containsKey(id);
    }

    /** Register demo-domain cleanup performed after a post is deleted. */
    public PostService onDelete(Consumer<String> listener) {
        this.deleteListener = Objects.requireNonNull(listener, "listener");
        return this;
    }

    public String create(final Post post) {
        FormMutationResult result = createResult(post);
        if (!result.succeeded()) throw new IllegalArgumentException(result.message());
        return result.entityId();
    }

    public FormMutationResult createResult(final Post post) {
        FormMutationResult invalid = validate(post);
        if (invalid != null) return invalid;
        String id = String.valueOf(idGenerator.getAndIncrement());
        Post newPost = new Post(id, post.title(), Objects.requireNonNullElse(post.content(), ""));
        posts.put(id, newPost);
        return FormMutationResult.saved(id, "Post created.");
    }

    public boolean update(final String id, final Post post) {
        return updateResult(id, post).succeeded();
    }

    public FormMutationResult updateResult(final String id, final Post post) {
        if (!exists(id)) return FormMutationResult.notFound("The post no longer exists.");
        FormMutationResult invalid = validate(post);
        if (invalid != null) return invalid;
        posts.put(id, new Post(id, post.title(), Objects.requireNonNullElse(post.content(), "")));
        return FormMutationResult.saved(id, "Post updated.");
    }

    public boolean delete(final String id) {
        if (id == null || posts.remove(id) == null) return false;
        deleteListener.accept(id);
        return true;
    }

    public FormMutationResult deleteResult(final String id) {
        return delete(id)
                ? FormMutationResult.saved(id, "Post deleted.")
                : FormMutationResult.notFound("The post no longer exists.");
    }

    /**
     * Find a post by its title (case-insensitive).
     *
     * @param title the title to search for
     * @return the matching post, or empty if not found
     */
    public Optional<Post> findByTitle(final String title) {
        return posts.values().stream()
                .filter(p -> p.title().equalsIgnoreCase(title))
                .findFirst();
    }

    public int bulkDelete(final Set<String> ids) {
        return deleteAll(ids).deletedIds().size();
    }

    public DeleteResult deleteAll(final Set<String> ids) {
        Set<String> deleted = new LinkedHashSet<>();
        Set<String> failed = new LinkedHashSet<>();
        for (String id : ids) {
            if (delete(id)) {
                deleted.add(id);
            } else {
                failed.add(id);
            }
        }
        return new DeleteResult(deleted, failed);
    }

    private Comparator<Post> comparator(ListQuery query) {
        String field = query.sort() == null ? "title" : query.sort().field();
        Comparator<Post> comparator = switch (field) {
            case "id" -> Comparator.comparingInt(post -> numericId(post.id()));
            case "title" -> Comparator.comparing(Post::title, String.CASE_INSENSITIVE_ORDER);
            case "content" -> Comparator.comparing(Post::content, String.CASE_INSENSITIVE_ORDER);
            default -> throw new IllegalArgumentException("Unsupported post sort field: " + field);
        };
        if (query.sort() != null && query.sort().direction() == SortDirection.DESC) {
            comparator = comparator.reversed();
        }
        return comparator.thenComparing(Post::id, String.CASE_INSENSITIVE_ORDER);
    }

    private static boolean matchesSearch(Post post, String search) {
        if (search == null || search.isBlank()) return true;
        String needle = search.toLowerCase(Locale.ROOT);
        return post.id().toLowerCase(Locale.ROOT).contains(needle)
                || post.title().toLowerCase(Locale.ROOT).contains(needle)
                || post.content().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static boolean matchesFilters(Post post, Map<String, String> filters) {
        return contains(post.title(), filters.get("title"))
                && contains(post.content(), filters.get("content"));
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

    private static FormMutationResult validate(Post post) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        if (post == null) {
            return FormMutationResult.failure("Post data is required.");
        }
        if (post.title() == null || post.title().isBlank()) {
            errors.put("title", List.of("Title is required"));
        } else if (post.title().length() > 200) {
            errors.put("title", List.of("Title must be at most 200 characters"));
        }
        if (post.content() != null && post.content().length() > 10_000) {
            errors.put("content", List.of("Content must be at most 10000 characters"));
        }
        return errors.isEmpty() ? null : FormMutationResult.invalid(errors);
    }
}
