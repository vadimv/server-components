package rsp.http;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Immutable HTTP headers preserving field order and repeated values. */
public final class HttpHeaders implements Iterable<HttpHeader> {
    public static final HttpHeaders EMPTY = new HttpHeaders(List.of());

    private final List<HttpHeader> values;

    private HttpHeaders(List<HttpHeader> values) {
        this.values = List.copyOf(values);
    }

    public static HttpHeaders of(HttpHeader... values) {
        return new HttpHeaders(List.of(values));
    }

    public static HttpHeaders copyOf(Iterable<HttpHeader> values) {
        Builder builder = builder();
        values.forEach(builder::add);
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> first(String name) {
        String normalized = normalize(name);
        return values.stream()
                .filter(header -> normalize(header.name()).equals(normalized))
                .map(HttpHeader::value)
                .findFirst();
    }

    public List<String> all(String name) {
        String normalized = normalize(name);
        return values.stream()
                .filter(header -> normalize(header.name()).equals(normalized))
                .map(HttpHeader::value)
                .toList();
    }

    public boolean contains(String name) {
        return first(name).isPresent();
    }

    public int size() {
        return values.size();
    }

    public List<HttpHeader> values() {
        return values;
    }

    @Override
    public Iterator<HttpHeader> iterator() {
        return values.iterator();
    }

    private static String normalize(String name) {
        return Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
    }

    public static final class Builder {
        private final List<HttpHeader> values = new ArrayList<>();

        public Builder add(String name, String value) {
            return add(new HttpHeader(name, value));
        }

        public Builder add(HttpHeader header) {
            values.add(Objects.requireNonNull(header, "header"));
            return this;
        }

        public Builder addAll(HttpHeaders headers) {
            values.addAll(Objects.requireNonNull(headers, "headers").values);
            return this;
        }

        public Builder set(String name, String value) {
            String normalized = normalize(name);
            values.removeIf(header -> normalize(header.name()).equals(normalized));
            return add(name, value);
        }

        public HttpHeaders build() {
            return values.isEmpty() ? EMPTY : new HttpHeaders(values);
        }
    }
}
