package rsp.compositions;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Router {
    private final Map<String, Class<? extends ViewContract>> routes = new HashMap<>();

    public Router route(String path, Class<? extends ViewContract> contractClass) {
        routes.put(path, contractClass);
        return this;
    }

    public Optional<Class<? extends ViewContract>> match(String path) {
        // Simple exact match for now. Trie or regex can be added later.
        // Also need to handle query params stripping if path contains them.
        String cleanPath = path.contains("?") ? path.substring(0, path.indexOf("?")) : path;
        return Optional.ofNullable(routes.get(cleanPath));
    }
}
