package rsp.url;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Represents a componentPath.
 * A componentPath could be either absolute or relative.
 * @param isAbsolute true if the componentPath is absolute, false otherwise
 * @param elements the componentPath's elements, must not be null
 */
public record Path(boolean isAbsolute, String[] elements) {
    public static final Path EMPTY = Path.of("");
    public static final Path ROOT = Path.of("/");

    public Path {
        elements = Objects.requireNonNull(elements, "elements").clone();
        for (String element : elements) {
            Objects.requireNonNull(element, "path element");
        }
    }

    @Override
    public String[] elements() {
        return elements.clone();
    }

    /**
     * Creates a new instance of a componentPath from a string.
     * @param pathStr a componentPath string where componentPath elements separated by '/';
     *                 if it starts with '/' then the created componentPath is absolute, otherwise it is relative
     * @return a componentPath object
     */
    public static Path of(final String pathStr) {
        Objects.requireNonNull(pathStr, "pathStr");
        return from(pathStr, false);
    }

    /** Parses an RFC 3986 path, percent-decoding each segment as UTF-8. */
    public static Path parse(final String rawPath) {
        Objects.requireNonNull(rawPath, "rawPath");
        return from(rawPath, true);
    }

    /** Creates a path from already decoded elements. */
    public static Path fromElements(boolean absolute, String... elements) {
        return new Path(absolute, elements);
    }

    private static Path from(String value, boolean decode) {
        boolean absolute = value.startsWith("/");
        String[] tokens = Arrays.stream(value.split("/"))
                .filter(segment -> !segment.isEmpty())
                .map(segment -> decode ? decodeSegment(segment) : segment)
                .toArray(String[]::new);
        return new Path(absolute, tokens);
    }


    /**
     * Resolves a componentPath to another componentPath.
     * If the provided componentPath is absolute the result is this componentPath, otherwise append its elements.
     * @param path the componentPath to resolve, must not be null
     * @return the result componentPath
     */
    public Path resolve(final Path path) {
        Objects.requireNonNull(path);
        if (path.isAbsolute) {
            return path;
        }
        String[] resolved = Arrays.copyOf(elements, elements.length + path.elements.length);
        System.arraycopy(path.elements, 0, resolved, elements.length, path.elements.length);
        return new Path(this.isAbsolute, resolved);
    }

    /**
     * Relativizes this path against the given base path.
     * @param basePath the base path, must not be null
     * @return the relative path
     * @throws IllegalArgumentException if this path does not start with the base path
     */
    public Path relativize(final Path basePath) {
        Objects.requireNonNull(basePath);
        if (!startsWith(basePath)) {
            throw new IllegalArgumentException("Path " + this + " does not start with " + basePath);
        }
        final String[] newElements = Arrays.copyOfRange(elements, basePath.elements.length, elements.length);
        return new Path(false, newElements);
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
    public int elementsCount() {
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
        final String elementsString = Arrays.stream(elements)
                .map(Path::encodeSegment)
                .collect(java.util.stream.Collectors.joining("/"));
        return isAbsolute ? "/" + elementsString : elementsString;
    }

    /**
     * Gets the last element of the componentPath.
     * @return the last element or null if the path is empty
     */
    public String last() {
        return elements.length > 0 ? elements[elements.length - 1] : null;
    }

    /**
     * Checks if the componentPath's last element equals to the provided string.
     * @param s the string to check, must not be null
     * @return true if the componentPath ends with the element and false if it is not
     */
    public boolean endsWith(final String s) {
        Objects.requireNonNull(s);
        return elements.length != 0 && elements[elements.length - 1].equals(s);
    }

    /**
     * Checks if the componentPath's first element equals to the provided string.
     * @param s the string to check, must not be null
     * @return true if the componentPath starts with the element and false if it is not
     */
    public boolean startsWith(final String s) {
        Objects.requireNonNull(s);
        return elements.length != 0 && elements[0].equals(s);
    }

    public boolean startsWith(final Path path) {
        Objects.requireNonNull(path);
        if (this.isAbsolute != path.isAbsolute || this.elements.length < path.elements.length) {
            return false;
        }

        for (int i = 0; i < path.elements.length; i++) {
            if (!path.elements[i].equals(this.elements[i])) {
                return false;
            }
        }
        return true;
    }

    public boolean contains(final String s) {
        Objects.requireNonNull(s);
        for (int i = 0; i < elements.length;i++) {
            if (elements[i].equals(s)) return true;
        }
        return false;
    }

    public boolean matches(final String regex) {
        Objects.requireNonNull(regex);
        return this.toString().matches(regex);
    }

    public boolean matches(final Pattern regex) {
        return Objects.requireNonNull(regex, "regex").matcher(toString()).matches();
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

    private static String decodeSegment(String raw) {
        StringBuilder result = new StringBuilder(raw.length());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < raw.length();) {
            if (raw.charAt(i) != '%') {
                result.append(raw.charAt(i++));
                continue;
            }
            bytes.reset();
            while (i < raw.length() && raw.charAt(i) == '%') {
                if (i + 2 >= raw.length()) {
                    throw new IllegalArgumentException("Malformed percent escape in path: " + raw);
                }
                int high = Character.digit(raw.charAt(i + 1), 16);
                int low = Character.digit(raw.charAt(i + 2), 16);
                if (high < 0 || low < 0) {
                    throw new IllegalArgumentException("Malformed percent escape in path: " + raw);
                }
                bytes.write((high << 4) | low);
                i += 3;
            }
            result.append(bytes.toString(StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private static String encodeSegment(String decoded) {
        StringBuilder result = new StringBuilder(decoded.length());
        for (byte value : decoded.getBytes(StandardCharsets.UTF_8)) {
            int c = value & 0xff;
            if (isPathSegmentCharacter(c)) {
                result.append((char) c);
            } else {
                result.append('%');
                result.append(Character.toUpperCase(Character.forDigit((c >>> 4) & 0xf, 16)));
                result.append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return result.toString();
    }

    private static boolean isPathSegmentCharacter(int c) {
        return c >= 'a' && c <= 'z'
                || c >= 'A' && c <= 'Z'
                || c >= '0' && c <= '9'
                || "-._~!$&'()*+,;=:@".indexOf(c) >= 0;
    }
}
