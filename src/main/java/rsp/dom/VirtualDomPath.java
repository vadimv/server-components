package rsp.dom;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public final class VirtualDomPath {
    public static final String SEPARATOR = "_";
    public static final VirtualDomPath DOCUMENT = VirtualDomPath.of("1");
    public static final VirtualDomPath WINDOW = VirtualDomPath.of("0");

    private final int[] array;

    public VirtualDomPath(int... xs) {
        array = xs;
    }

    public static VirtualDomPath of(String path) {
        return new VirtualDomPath(Arrays.stream(path.split(SEPARATOR)).mapToInt(s -> Integer.parseInt(s)).toArray());
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

    public Optional<VirtualDomPath> parent() {
        if (this.equals(WINDOW)) {
            return Optional.empty();
        } else if (this.equals(DOCUMENT)) {
            return Optional.of(WINDOW);
        } else {
            return Optional.of(take(array.length - 1));
        }
    }

    public VirtualDomPath childNumber(int num) {
        final VirtualDomPath childPath = incLevel();
        childPath.array[childPath.level() - 1] = num;
        return childPath;
    }

    private VirtualDomPath take(int level) {
        int[] na = new int[level];
        System.arraycopy(array, 0, na, 0, level);
        return new VirtualDomPath(na);
    }

    @Override
    public String toString() {
        if (array.length == 0) {
            return "";
        } else {
            return String.join(SEPARATOR, Arrays.stream(array).mapToObj(Integer::toString).collect(Collectors.toList()));
        }
    }

    @Override
    public boolean equals(Object other) {
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
