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
        final int[] a = Arrays.copyOf(array, array.length);
        a[a.length - 1]++;
        return new TreePositionPath(a);
    }

    public TreePositionPath parent() {
        return take(array.length - 1);
    }

    public TreePositionPath add(final TreePositionPath otherPath) {
        final int[] a = new int[array.length + otherPath.array.length];
        System.arraycopy(array, 0, a, 0, array.length);
        System.arraycopy(otherPath.array, 0, a, array.length, otherPath.array.length);
        return new TreePositionPath(a);
    }

    public TreePositionPath addChild(final int num) {
        final TreePositionPath childPath = incLevel();
        childPath.array[childPath.level() - 1] = num;
        return childPath;
    }

    public TreePositionPath childNumber(final int num) {
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
