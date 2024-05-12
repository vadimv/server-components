package rsp.dom;

import java.util.Arrays;

public final class TreePositionPath {
    public static final String SEPARATOR = "_";

    private final int[] array;

    public TreePositionPath(final int... xs) {
        array = xs;
    }

    public static TreePositionPath of(final String path) {
        return path.isBlank() ? new TreePositionPath() : new TreePositionPath(Arrays.stream(path.split(SEPARATOR)).mapToInt(Integer::parseInt).toArray());
    }

    public int level() {
        return array.length;
    }

    public TreePositionPath incLevel() {
        final int[] a = Arrays.copyOf(array, array.length + 1);
        a[a.length - 1] = 1;
        return new TreePositionPath(a);
    }

    public TreePositionPath incSibling() {
        if (array.length > 0) {
            final int[] a = Arrays.copyOf(array, array.length);
            a[a.length - 1]++;
            return new TreePositionPath(a);
        } else {
            throw new IllegalStateException("It is not possible to get a sibling of a root path");
        }
    }

    public TreePositionPath parent() {
        if (array.length > 0) {
            return take(array.length - 1);
        } else {
            throw new IllegalStateException("It is not possible to get a parent of a root path");
        }
    }

    public TreePositionPath addChild(final int num) {
        final TreePositionPath childPath = incLevel();
        childPath.array[childPath.level() - 1] = num;
        return childPath;
    }

    private TreePositionPath take(final int level) {
        final int[] na = new int[level];
        System.arraycopy(array, 0, na, 0, level);
        return new TreePositionPath(na);
    }

    @Override
    public String toString() {
        if (array.length == 0) {
            return "";
        } else {
            return String.join(SEPARATOR, Arrays.stream(array).mapToObj(Integer::toString).toList());
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof TreePositionPath) {
            return Arrays.equals(array, ((TreePositionPath) other).array);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

}
