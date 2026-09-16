package rsp.url.routing;

import rsp.url.Path;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** An absolute path template containing literal and {@code {name}} segments. */
public final class RouteTemplate {
    private final String source;
    private final List<Segment> segments;
    private final int literalCount;

    private RouteTemplate(String source, List<Segment> segments) {
        this.source = source;
        this.segments = List.copyOf(segments);
        this.literalCount = (int) segments.stream().filter(Segment::literal).count();
    }

    public static RouteTemplate parse(String source) {
        Objects.requireNonNull(source, "source");
        if (!source.startsWith("/")) {
            throw new IllegalArgumentException("A route template must be absolute: " + source);
        }
        if (source.length() > 1 && source.endsWith("/")) {
            throw new IllegalArgumentException("A route template must not have a trailing '/': " + source);
        }

        List<Segment> segments = new ArrayList<>();
        HashSet<String> parameterNames = new HashSet<>();
        if (!source.equals("/")) {
            for (String rawSegment : source.substring(1).split("/", -1)) {
                if (rawSegment.isEmpty()) {
                    throw new IllegalArgumentException("A route template cannot contain an empty segment: " + source);
                }
                if (rawSegment.startsWith("{") && rawSegment.endsWith("}")) {
                    String name = rawSegment.substring(1, rawSegment.length() - 1);
                    if (!name.matches("[A-Za-z][A-Za-z0-9_]*")) {
                        throw new IllegalArgumentException("Invalid route parameter name: " + rawSegment);
                    }
                    if (!parameterNames.add(name)) {
                        throw new IllegalArgumentException("Duplicate route parameter '" + name + "': " + source);
                    }
                    segments.add(Segment.parameter(name));
                } else {
                    if (rawSegment.indexOf('{') >= 0 || rawSegment.indexOf('}') >= 0) {
                        throw new IllegalArgumentException("A parameter must occupy a complete segment: " + rawSegment);
                    }
                    Path decoded = Path.parse('/' + rawSegment);
                    if (decoded.elementsCount() != 1) {
                        throw new IllegalArgumentException("Invalid literal route segment: " + rawSegment);
                    }
                    segments.add(Segment.literal(decoded.get(0)));
                }
            }
        }
        return new RouteTemplate(canonical(segments), segments);
    }

    public Optional<PathMatch> match(Path path) {
        Objects.requireNonNull(path, "path");
        if (!path.isAbsolute() || path.elementsCount() != segments.size()) {
            return Optional.empty();
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            String actual = path.get(i);
            if (segment.literal()) {
                if (!segment.value().equals(actual)) {
                    return Optional.empty();
                }
            } else {
                parameters.put(segment.value(), actual);
            }
        }
        return Optional.of(parameters.isEmpty() ? PathMatch.EMPTY : new PathMatch(parameters));
    }

    public String expand(Map<String, ?> parameters) {
        Objects.requireNonNull(parameters, "parameters");
        String[] elements = new String[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (segment.literal()) {
                elements[i] = segment.value();
            } else {
                Object value = parameters.get(segment.value());
                if (value == null) {
                    throw new IllegalArgumentException("Missing route parameter '" + segment.value() + "'");
                }
                elements[i] = value.toString();
            }
        }
        return Path.fromElements(true, elements).toString();
    }

    public String expand(Object... values) {
        Objects.requireNonNull(values, "values");
        List<String> names = parameterNames();
        if (values.length != names.size()) {
            throw new IllegalArgumentException("Expected " + names.size() + " route values, got " + values.length);
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            parameters.put(names.get(i), Objects.requireNonNull(values[i], "route value"));
        }
        return expand(parameters);
    }

    public Optional<RouteTemplate> parent() {
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RouteTemplate(canonical(segments.subList(0, segments.size() - 1)),
                segments.subList(0, segments.size() - 1)));
    }

    public List<String> parameterNames() {
        return segments.stream().filter(segment -> !segment.literal()).map(Segment::value).toList();
    }

    int literalCount() {
        return literalCount;
    }

    boolean overlaps(RouteTemplate other) {
        if (segments.size() != other.segments.size()) {
            return false;
        }
        for (int i = 0; i < segments.size(); i++) {
            Segment left = segments.get(i);
            Segment right = other.segments.get(i);
            if (left.literal() && right.literal() && !left.value().equals(right.value())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return source;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RouteTemplate template && segments.equals(template.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    private static String canonical(List<Segment> segments) {
        if (segments.isEmpty()) {
            return "/";
        }
        return "/" + segments.stream()
                .map(segment -> segment.literal()
                        ? Path.fromElements(false, segment.value()).toString()
                        : "{" + segment.value() + "}")
                .collect(java.util.stream.Collectors.joining("/"));
    }

    private record Segment(boolean literal, String value) {
        private Segment {
            Objects.requireNonNull(value, "value");
        }

        static Segment literal(String value) {
            return new Segment(true, value);
        }

        static Segment parameter(String name) {
            return new Segment(false, name);
        }
    }
}
