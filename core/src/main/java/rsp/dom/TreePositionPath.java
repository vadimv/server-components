package rsp.dom;

import java.util.Arrays;
import java.util.Objects;

/**
 * This immutable class represents a node's position in a tree.
 * A position is represented by a sequence of integers where every integer's index corresponds to some level of a tree
 * and the integer itself defines a number of the branch starting from 1.
 * The root node's position can be empty or can be 1.
 * For example: considering 1 is the root, 1_2 is the path to the second sibling node of on the second level of this tree.
 */
public final class TreePositionPath {
    /**
     * The separator used for a string representation of an object of this class
     */
    public static final String SEPARATOR = "_";

    private final int[] elements;

    public TreePositionPath(final int... elements) {
        this.elements = elements;
    }

    public static TreePositionPath of(final String path) {
        Objects.requireNonNull(path);
        return path.isBlank() ? new TreePositionPath() : new TreePositionPath(Arrays.stream(path.split(SEPARATOR)).mapToInt(Integer::parseInt).toArray());
    }

    public int elementAt(final int i) {
        if (i < 0 || i >= elements.length) {
            throw new IllegalArgumentException("Index " + i + " out of bounds for path length " + elements.length);
        }
        return elements[i];
    }

    public int elementsCount() {
        return elements.length;
    }

    public TreePositionPath incLevel() {
        final int[] a = Arrays.copyOf(elements, elements.length + 1);
        a[a.length - 1] = 1;
        return new TreePositionPath(a);
    }

    public TreePositionPath incSibling() {
        if (elements.length > 0) {
            final int[] a = Arrays.copyOf(elements, elements.length);
            a[a.length - 1]++;
            return new TreePositionPath(a);
        } else {
            throw new IllegalStateException("It is not possible to get a sibling of a root path");
        }
    }

    public TreePositionPath parent() {
        if (elements.length > 0) {
            return take(elements.length - 1);
        } else {
            throw new IllegalStateException("It is not possible to get a parent of a root path");
        }
    }

    public TreePositionPath addChild(final int num) {
        final TreePositionPath childPath = incLevel();
        childPath.elements[childPath.elementsCount() - 1] = num;
        return childPath;
    }

    private TreePositionPath take(final int level) {
        final int[] na = new int[level];
        System.arraycopy(elements, 0, na, 0, level);
        return new TreePositionPath(na);
    }

    @Override
    public String toString() {
        if (elements.length == 0) {
            return "";
        } else {
            return String.join(SEPARATOR, Arrays.stream(elements).mapToObj(Integer::toString).toList());
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof TreePositionPath) {
            return Arrays.equals(elements, ((TreePositionPath) other).elements);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(elements);
    }

}
