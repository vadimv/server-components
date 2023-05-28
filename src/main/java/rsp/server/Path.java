package rsp.server;

import rsp.util.ArrayUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
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
    public Path(final boolean isAbsolute, final String... elements) {
        this.isAbsolute = isAbsolute;
        this.elements = elements;
    }

    /**
     * Creates a new instance of an absolute path from its segments.
     * @param segments paths segments
     * @return a path object
     */
    public static Path absolute(final String... segments) {
        return new Path(true, segments);
    }

    /**
     * Creates a new instance of a relative path from its segments.
     * @param segments paths segments
     * @return a path object
     */
    public static Path relative(final String... segments) {
        return new Path(false, segments);
    }

    /**
     * Creates a new instance of a path from a string.
     * @param str the string with '/' separated path elements;
     *            if starts with '/' then the path is absolute, otherwise the path is relative
     * @return a path object
     */
    public static Path of(final String str) {
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
    public Path resolve(final Path path) {
        if (path.isAbsolute) {
            return path;
        }
        return new Path(this.isAbsolute, ArrayUtils.concat(this.elements, path.elements));
    }

    public Path relativize(final Path path) {
        return new Path(false);// TODO
    }

    /**
     * Returns the element at the specified position in this path.
     * @param index index of the element to return
     * @return the element at the specified position in this path
     * @throws IllegalArgumentException if index is out of this path elements number,
     *         or this path has zero elements
     */
    public String get(final int index) {
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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Path path = (Path) o;
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
    public boolean endsWith(final String s) {
        return elements.length != 0 && elements[elements.length - 1].equals(s);
    }

    /**
     * Checks if the path's first element equals to the provided string.
     * @param s the string to check
     * @return true if the path starts with the element and false if it is not
     */
    public boolean startsWith(final String s) {
        return elements.length != 0 && elements[0].equals(s);
    }
}
