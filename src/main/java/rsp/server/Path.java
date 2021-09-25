package rsp.server;

import rsp.util.ArrayUtils;
import rsp.util.TriFunction;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Represents a path.
 * A path could be either absolute or relative.
 */
public final class Path {
    public static final Path EMPTY_ABSOLUTE = new Path(true);
    public static final Path EMPTY_RELATIVE = new Path(false);

    public final boolean isAbsolute;
    public final String[] elements;

    /**
     * Creates a new instance of a path.
     * @param isAbsolute true if the path is absolute, false is the path is relative
     * @param elements the path's elements
     */
    public Path(boolean isAbsolute, String... elements) {
        this.isAbsolute = isAbsolute;
        this.elements = elements;
    }

    /**
     * Creates a new instance of a path from a string.
     * @param str the string with '/' separated path elements;
     *            if starts with '/' then the path is absolute, otherwise the path is relative
     * @return a path object
     */
    public static Path of(String str) {
        final String trimmedStr = str.trim();
        final String[] tokens = Arrays.stream(trimmedStr.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
        return new Path(trimmedStr.startsWith("/"), tokens);
    }

    /**
     * Resolves a path to another path.
     * If the provided path is absolute the result is this path, otherwise append its elements.
     * @param path the path to resolve
     * @return the result path
     */
    public Path resolve(Path path) {
        if (path.isAbsolute) {
            return path;
        }
        return new Path(this.isAbsolute, ArrayUtils.concat(this.elements, path.elements));
    }

    public Path relativize(Path path) {
        return new Path(false);// TODO
    }

    /**
     * Returns the element at the specified position in this path.
     * @param index index of the element to return
     * @return the element at the specified position in this path
     * @throws IllegalArgumentException if index is out of this path elements number,
     *         or this path has zero elements
     */
    public String get(int index) {
        if (index >=0 && index < elements.length)
        {
            return elements[index];
        } else {
            throw new IllegalArgumentException("Path index: " + index + " , elements number: " + elements.length);
        }
    }

    /**
     * The path's elements number.
     * @return the path's length
     */
    public int size() {
        return elements.length;
    }

    /**
     * Checks if the path is empty or not.
     * @return true if the path is empty, false otherwise
     */
    public boolean isEmpty() {
        return elements.length == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Path path = (Path) o;
        return isAbsolute == path.isAbsolute &&
                Arrays.equals(elements, path.elements);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(isAbsolute);
        result = 31 * result + Arrays.hashCode(elements);
        return result;
    }

    /**
     * Converts the path to the stream of its elements
     * @return
     */
    public Stream<String> stream() {
        return Arrays.stream(elements);
    }

    public String toString() {
        final String elementsString = String.join("/", elements);
        return isAbsolute ? "/" + elementsString : elementsString;
    }

    /**
     * Gets the last element of the path.
     * @return the last element
     */
    public Optional<String> last() {
        return elements.length > 0 ? Optional.of(elements[elements.length - 1]) : Optional.empty();
    }

    /**
     * Checks if the path's last element equals to the provided string.
     * @param s the string to check
     * @return true if the path ends with the element and false if it is not
     */
    public boolean endsWith(String s) {
        return elements.length != 0 && elements[elements.length - 1].equals(s);
    }

    /**
     * Checks if the path's first element equals to the provided string.
     * @param s the string to check
     * @return true if the path starts with the element and false if it is not
     */
    public boolean startsWith(String s) {
        return elements.length != 0 && elements[0].equals(s);
    }

    /**
     * Tests if a path matches with an empty path.
     */
    @FunctionalInterface
    public interface Match0 {
        boolean test();
    }

    /**
     * Tests if a path with one element satisfies a condition.
     */
    @FunctionalInterface
    public interface Match1 {
        boolean test(String p0);
    }

    /**
     * Tests if a path with two elements satisfies a condition.
     */
    @FunctionalInterface
    public interface Match2 {
        boolean test(String p0, String p1);
    }

    /**
     * Tests if a path with three elements satisfies a condition.
     */
    @FunctionalInterface
    public interface Match3 {
        boolean test(String p0, String p1, String p2);
    }

    /**
     * Provides a framework for Path matching according to the path elements numbers.
     * The match overloaded methods define the patterns and corresponded function with destructed path elements as its parameter.
     * The match methods allow to create a chain of patterns.
     * The first match results the state.
     *
     * @param <S> a state type
     */
    public static class Matcher<S> {
        private final Path path;

        /**
         * Indicates if a match happened or not.
         * Initially set to false, set to true in case of a match.
         */
        public final boolean isMatch;

        /**
         * The match result.
         */
        public final CompletableFuture<? extends S> state;

        private Matcher(Path path, CompletableFuture<? extends S> defaultState, boolean isMatch) {
            this.path = path;
            this.isMatch = isMatch;
            this.state = defaultState;
        }

        /**
         * Creates a new instance of a matcher.
         * @param path the path to match
         * @param defaultState the result state when the path has no matches in the form of a CompletableFuture
         */
        public Matcher(Path path, CompletableFuture<S> defaultState) {
            this(path, defaultState, false);
        }

        /**
         * Creates a new instance of a matcher.
         * @param path the path to match
         * @param defaultState the result state when the path has no matches
         */
        public Matcher(Path path, S defaultState) {
            this(path, CompletableFuture.completedFuture(defaultState));
        }

        /**
         * Matches to the empty path pattern.
         * @param predicate the condition for match, if the path matches and the predicate is true then the provided
         *                  state is setup as the matches chain result
         * @param state the state's supplier for this match attempt
         * @return a next instance in the matcher's chain
         */
        public Matcher<S> match(Match0 predicate, Supplier<CompletableFuture<S>> state) {
            if (!isMatch && this.path.isEmpty()) {
                return new Matcher<>(path, state.get(), true);
            } else {
                return this;
            }
        }

        /**
         * Matches to a path with one element pattern.
         * @param predicate the condition for match, if the path matches and the predicate is true then the provided
         *                  state is setup as the matches chain result
         * @param state the state function for this match attempt
         * @return a next instance in the matcher's chain
         */
        public Matcher<S> match(Match1 predicate, Function<String, CompletableFuture<? extends S>> state) {
            if (!isMatch
                && path.elements.length == 1
                && predicate.test(path.elements[0])) {
                return new Matcher<S>(path, state.apply(path.elements[0]), true);
            } else {
                return this;
            }
        }

        /**
         * Matches to a path with two elements pattern.
         * @param predicate the condition for match, if the path matches and the predicate is true then the provided
         *                  state is setup as the matches chain result
         * @param state the state function for this match attempt
         * @return a next instance in the matcher's chain
         */
        public Matcher<S> match(Match2 predicate, BiFunction<String, String, CompletableFuture<S>> state) {
            if (!isMatch
                && path.elements.length == 2
                && predicate.test(path.elements[0], path.elements[1])) {
                return new Matcher<>(path, state.apply(path.elements[0], path.elements[1]), true);
            } else {
                return this;
            }
        }

        /**
         * Matches to a path with three elements pattern.
         * @param predicate the condition for match, if the path matches and the predicate is true then the provided
         *                  state is setup as the matches chain result
         * @param state the state function for this match attempt
         * @return a next instance in the matcher's chain
         */
        public Matcher<S> match(Match3 predicate, TriFunction<String, String, String, CompletableFuture<S>> state) {
            if (!isMatch
                && path.elements.length == 3
                && predicate.test(path.elements[0], path.elements[1], path.elements[2])) {
                return new Matcher<>(path, state.apply(path.elements[0], path.elements[1], path.elements[2]), true);
            } else {
                return this;
            }
        }
    }
}
