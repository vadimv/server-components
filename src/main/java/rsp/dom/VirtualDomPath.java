package rsp.dom;

import java.util.Arrays;

public final class VirtualDomPath {
    public static final String SEPARATOR = "_";

    private final int[] array;

    public VirtualDomPath(final int... xs) {
        array = xs;
    }

    public static VirtualDomPath of(final String path) {
        return path.isBlank() ? new VirtualDomPath() : new VirtualDomPath(Arrays.stream(path.split(SEPARATOR)).mapToInt(Integer::parseInt).toArray());
    }

    public int level() {
        return array.length;
    }

    public VirtualDomPath incLevel() {
        final int[] a = Arrays.copyOf(array, array.length + 1);
        a[a.length - 1] = 1;
        return new VirtualDomPath(a);
    }

    public VirtualDomPath incSibling() {
        final int[] a = Arrays.copyOf(array, array.length);
        a[a.length - 1]++;
        return new VirtualDomPath(a);
    }

    public VirtualDomPath parent() {
        return take(array.length - 1);
    }

    public VirtualDomPath add(final VirtualDomPath otherPath) {
        final int[] a = new int[array.length + otherPath.array.length];
        System.arraycopy(array, 0, a, 0, array.length);
        System.arraycopy(otherPath.array, 0, a, array.length, otherPath.array.length);
        return new VirtualDomPath(a);
    }

    public VirtualDomPath childNumber(final int num) {
        final VirtualDomPath childPath = incLevel();
        childPath.array[childPath.level() - 1] = num;
        return childPath;
    }

    private VirtualDomPath take(final int level) {
        final int[] na = new int[level];
        System.arraycopy(array, 0, na, 0, level);
        return new VirtualDomPath(na);
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
        if (other instanceof VirtualDomPath) {
            return Arrays.equals(array, ((VirtualDomPath) other).array);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

}
