package rsp.url.routing;

import java.util.Map;
import java.util.Objects;

/** Named values extracted while matching a path template. */
public record PathMatch(Map<String, String> parameters) {
    public static final PathMatch EMPTY = new PathMatch(Map.of());

    public PathMatch {
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }

    public String required(String name) {
        String value = parameters.get(Objects.requireNonNull(name, "name"));
        if (value == null) {
            throw new IllegalArgumentException("No path parameter named '" + name + "'");
        }
        return value;
    }
}
