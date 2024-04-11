package rsp.server;

import rsp.util.ArrayUtils;

import java.util.*;
import java.util.stream.Stream;

/**
 * Represents a path.
 * A path could be either absolute or relative.
 */
public final class Path {
    public static final Path EMPTY = Path.of("");
    public static final Path ROOT = Path.of("/");

    private final boolean isAbsolute;
    private final String[] elements;

    /**
     * Creates a new instance of a path.
     * @param elements the path's elements
     */
    private Path(final boolean isAbsolute, final String[] elements) {
        this.isAbsolute = isAbsolute;
        this.elements = elements;
    }

    /**
     * Creates a new instance of a path from a string.
     * @param pathStr a path string where path elements separated by '/';
     *                 if it starts with '/' then the created path is absolute, otherwise it is relative
     * @return a path object
     */
    public static Path of(final String pathStr) {
        Objects.requireNonNull(pathStr);

        final String trimmedStr = pathStr.trim();
        final String[] tokens = Arrays.stream(trimmedStr.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
        return new Path(trimmedStr.startsWith("/"), tokens);
    }

    public boolean isAbsolute() {
        return isAbsolute;
    }

    public String[] elements() {
        return elements;
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

    /**
     * Returns the element at the specified position in this path.
     * @param index index of the element to return
     * @return the element at the specified position in this path
     * @throws IllegalArgumentException if index is out of this path elements number,
     *         or this path has zero elements
     */
    public String get(final int index) {
        if (index >=0 && index < elements.length) {
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
     * @return true if this path is empty, false otherwise
     */
    public boolean isEmpty() {
        return EMPTY.equals(this);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Path path = (Path) o;
        return isAbsolute == path.isAbsolute && Arrays.equals(elements, path.elements);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(isAbsolute);
        result = 31 * result + Arrays.hashCode(elements);
        return result;
    }
}
