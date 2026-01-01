package rsp.compositions.posts.services;

import rsp.compositions.posts.entities.Post;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PostService {
    private final Map<String, Post> posts = new ConcurrentHashMap<>();
    private final AtomicInteger idGenerator = new AtomicInteger(1);

    public PostService() {
        // Pre-populate with dummy data
        for (int i = 1; i <= 25; i++) {
            create(new Post(null, "Post Title " + i, "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " + i));
        }
    }

    public List<Post> findAll(int page, int pageSize, String sort) {
        Comparator<Post> comparator = Comparator.comparing(Post::title);
        if ("desc".equalsIgnoreCase(sort)) {
            comparator = comparator.reversed();
        }

        return posts.values().stream()
                .sorted(comparator)
                .skip((long) (page - 1) * pageSize)
                .limit(pageSize)
                .collect(Collectors.toList());
    }

    public Optional<Post> find(String id) {
        return Optional.ofNullable(posts.get(id));
    }

    public String create(Post post) {
        String id = String.valueOf(idGenerator.getAndIncrement());
        Post newPost = new Post(id, post.title(), post.content());
        posts.put(id, newPost);
        return id;
    }

    public boolean update(String id, Post post) {
        if (posts.containsKey(id)) {
            posts.put(id, new Post(id, post.title(), post.content()));
            return true;
        }
        return false;
    }

    public boolean delete(String id) {
        return posts.remove(id) != null;
    }
}
