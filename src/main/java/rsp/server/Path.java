package rsp.server;

import rsp.util.ArrayUtils;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Path {
    public static final Path EMPTY = new Path();

    private final String[] elements;

    public Path(String... elements) {
        this.elements = elements;
    }

    public static Path of(String str) {
        final String[] tokens = Arrays.stream(str.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
        return new Path(tokens);
    }

    public Path append(Path path) {
        return new Path(ArrayUtils.concat(this.elements, path.elements));
    }

    public Path relativize(Path path) {
        return new Path();// TODO
    }

    public int size() {
        return elements.length;
    }

    public boolean isEmpty() {
        return elements.length == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Path path = (Path) o;
        return Arrays.equals(elements, path.elements);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(elements);
    }

    public Stream<String> stream() {
        return Arrays.stream(elements);
    }

    public String toString() {
        return String.join("/", elements);
    }

    public <S> Matcher  matcher(CompletableFuture<S> defaultState) {
        return new Matcher<>(this, defaultState);
    }

    public Optional<String> last() {
        return elements.length > 0 ? Optional.of(elements[elements.length - 1]) : Optional.empty();
    }

    public boolean endsWith(String s) {
        return elements.length != 0 && elements[elements.length - 1].equals(s);
    }

    public boolean startsWith(String s) {
        return elements.length != 0 && elements[0].equals(s);
    }

    public static class Matcher<S> {
        private final Path path;

        public final boolean isMatch;
        public final CompletableFuture<S> state;

        public Matcher(Path path, CompletableFuture<S> defaultState, boolean isMatch) {
            this.path = path;
            this.isMatch = isMatch;
            this.state = defaultState;
        }

        public Matcher(Path path, CompletableFuture<S> defaultState) {
            this(path, defaultState, false);
        }

        public Matcher<S> whenEmpty(Supplier<S> state) {
            return whenEmpty(CompletableFuture.completedFuture(state.get()));
        }

        public Matcher<S> whenEmpty(CompletableFuture<S> state) {
            if (this.path.isEmpty()) {
                return new Matcher<>(path, state, true);
            } else {
                return this;
            }
        }

        public Matcher<S> when(Match1 predicate, Function<String, CompletableFuture<S>> state) {
            if (path.elements.length == 1 && predicate.test(path.elements[0])) {
                return new Matcher<>(path, state.apply(path.elements[0]), true);
            } else {
                return this;
            }
        }

        public Matcher<S> when(Match2 predicate, BiFunction<String, String, CompletableFuture<S>> state) {
            if (path.elements.length == 2 && predicate.test(path.elements[0], path.elements[1])) {
                return new Matcher<>(path, state.apply(path.elements[0], path.elements[1]), true);
            } else {
                return this;
            }
        }


        public Matcher<S> when(Match3 predicate, CompletableFuture<S> state) {
            if (path.elements.length == 3 && predicate.test(path.elements[0], path.elements[1], path.elements[2])) {
                return new Matcher<>(path, state, true);
            } else {
                return this;
            }
        }

        public Matcher<S> when(Match3 predicate, Supplier<S> state) {
            return when(predicate, CompletableFuture.completedFuture(state.get()));
        }


    }

    @FunctionalInterface
    public interface Match1 {
        boolean test(String p0);
    }

    @FunctionalInterface
    public interface Match2 {
        boolean test(String p0, String p1);
    }

    @FunctionalInterface
    public interface Match3 {
        boolean test(String p0, String p1, String p2);
    }

    @FunctionalInterface
    public interface Match4 {
        boolean test(String p0, String p1, String p2, String p3);
    }

    @FunctionalInterface
    public interface Match5 {
        boolean test(String p0, String p1, String p2, String p3, String p4);
    }
}
