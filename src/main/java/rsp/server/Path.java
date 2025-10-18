package rsp.server;

import rsp.util.ArrayUtils;

import java.util.*;
import java.util.stream.Stream;

/**
 * Represents a componentPath.
 * A componentPath could be either absolute or relative.
 */
public record Path(boolean isAbsolute, String[] elements) {
    public static final Path EMPTY = Path.of("");
    public static final Path ROOT = Path.of("/");

    /**
     * Creates a new instance of a componentPath from a string.
     * @param pathStr a componentPath string where componentPath elements separated by '/';
     *                 if it starts with '/' then the created componentPath is absolute, otherwise it is relative
     * @return a componentPath object
     */
    public static Path of(final String pathStr) {
        Objects.requireNonNull(pathStr);

        final String trimmedStr = pathStr.trim();
        final String[] tokens = Arrays.stream(trimmedStr.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
        return new Path(trimmedStr.startsWith("/"), tokens);
    }


    /**
     * Resolves a componentPath to another componentPath.
     * If the provided componentPath is absolute the result is this componentPath, otherwise append its elements.
     * @param path the componentPath to resolve
     * @return the result componentPath
     */
    public Path resolve(final Path path) {
        if (path.isAbsolute) {
            return path;
        }
        return new Path(this.isAbsolute, ArrayUtils.concat(this.elements, path.elements));
    }

    /**
     * Returns the element at the specified position in this componentPath.
     * @param index index of the element to return
     * @return the element at the specified position in this componentPath
     * @throws IllegalArgumentException if index is out of this componentPath elements number,
     *         or this componentPath has zero elements
     */
    public String get(final int index) {
        if (index >=0 && index < elements.length) {
            return elements[index];
        } else {
            throw new IllegalArgumentException("Path index: " + index + " , elements number: " + elements.length);
        }
    }

    /**
     * The componentPath's elements number.
     * @return the componentPath's length
     */
    public int size() {
        return elements.length;
    }

    /**
     * Checks if the componentPath is empty or not.
     * @return true if this componentPath is empty, false otherwise
     */
    public boolean isEmpty() {
        return elements.length == 0;
    }


    /**
     * Converts the componentPath to the stream of its elements
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
     * Gets the last element of the componentPath.
     * @return the last element
     */
    public Optional<String> last() {
        return elements.length > 0 ? Optional.of(elements[elements.length - 1]) : Optional.empty();
    }

    /**
     * Checks if the componentPath's last element equals to the provided string.
     * @param s the string to check
     * @return true if the componentPath ends with the element and false if it is not
     */
    public boolean endsWith(final String s) {
        return elements.length != 0 && elements[elements.length - 1].equals(s);
    }

    /**
     * Checks if the componentPath's first element equals to the provided string.
     * @param s the string to check
     * @return true if the componentPath starts with the element and false if it is not
     */
    public boolean startsWith(final String s) {
        return elements.length != 0 && elements[0].equals(s);
    }

    public boolean contains(final String s) {
        for (int i = 0; i < elements.length;i++) {
            if (elements[i].equals(s)) return true;
        }
        return false;
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
