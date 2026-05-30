package rsp.dom;

import java.util.Arrays;
import java.util.Objects;

/**
 * A DOM-node addressing identity used by the diff → protocol → client seam.
 *
 * <p>Unlike {@link TreePositionPath} (which is purely positional and is used for component and
 * event identity), a {@code NodeId} segment is either a positional index (a decimal string) or a
 * stable key segment ({@code "kn<decimal>"} / {@code "ks<escaped>"}) produced by the
 * {@link rsp.dsl.Html#key} directives. Keyed segments give a node an identity that survives
 * reordering, so the diff can move a node ({@code insertBefore}) instead of rewriting it, and the
 * client can keep the same element across renders.
 *
 * <p>The string form joins segments with {@code "_"} and is what travels on the wire and keys the
 * client's element map.
 */
public final class NodeId {

    public static final String SEPARATOR = "_";

    private final String[] segments;

    public NodeId(final String... segments) {
        this.segments = Objects.requireNonNull(segments);
    }

    /** The id of a node at the given positional path, all segments positional. */
    public static NodeId of(final TreePositionPath path) {
        Objects.requireNonNull(path);
        final String[] s = new String[path.elementsCount()];
        for (int i = 0; i < s.length; i++) {
            s[i] = Integer.toString(path.elementAt(i));
        }
        return new NodeId(s);
    }

    /** Parses a wire id string back into a {@code NodeId}. */
    public static NodeId of(final String s) {
        Objects.requireNonNull(s);
        return s.isBlank() ? new NodeId() : new NodeId(s.split(SEPARATOR));
    }

    /** Descends to the first child, appending the positional segment {@code "1"}. */
    public NodeId incLevel() {
        return child("1");
    }

    /** The next positional sibling: increments the last segment, which must be positional. */
    public NodeId incSibling() {
        if (segments.length == 0) {
            throw new IllegalStateException("It is not possible to get a sibling of a root id");
        }
        final String[] a = Arrays.copyOf(segments, segments.length);
        final String last = a[a.length - 1];
        try {
            a[a.length - 1] = Integer.toString(Integer.parseInt(last) + 1);
        } catch (final NumberFormatException e) {
            throw new IllegalStateException("Cannot increment a keyed segment as a sibling: " + last);
        }
        return new NodeId(a);
    }

    /** Appends an arbitrary child segment (a positional decimal or a key segment). */
    public NodeId child(final String segment) {
        Objects.requireNonNull(segment);
        final String[] a = Arrays.copyOf(segments, segments.length + 1);
        a[a.length - 1] = segment;
        return new NodeId(a);
    }

    public NodeId parent() {
        if (segments.length == 0) {
            throw new IllegalStateException("It is not possible to get a parent of a root id");
        }
        return new NodeId(Arrays.copyOf(segments, segments.length - 1));
    }

    public int elementsCount() {
        return segments.length;
    }

    public String lastSegment() {
        if (segments.length == 0) {
            throw new IllegalStateException("Root id has no last segment");
        }
        return segments[segments.length - 1];
    }

    @Override
    public String toString() {
        return String.join(SEPARATOR, segments);
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof NodeId n && Arrays.equals(segments, n.segments);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(segments);
    }
}
