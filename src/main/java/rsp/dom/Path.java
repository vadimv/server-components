package rsp.dom;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public class Path {
    public static final String SEPARATOR = "_";
    public static final Path WINDOW = Path.of("1");

    private final int[] array;

    public Path(int... xs) {
        array = xs;
    }

    public static Path of(String path) {
        return new Path(Arrays.stream(path.split(SEPARATOR)).mapToInt(s -> Integer.parseInt(s)).toArray());
    }

    public int level() {
        return array.length;
    }

    public Path incLevel() {
        final int[] a = Arrays.copyOf(array, array.length + 1);
        a[a.length - 1] = 1;
        return new Path(a);
    }

    public Path incSibling() {
        final int[] a = Arrays.copyOf(array, array.length);
        a[a.length - 1]++;
        return new Path(a);
    }

    public Optional<Path> parent() {
        if (array.length <= 1) {
            return Optional.empty();
        } else {
            return Optional.of(take(array.length - 1));
        }
    }

    public Path childNumber(int num) {
        final Path childPath = incLevel();
        childPath.array[childPath.level() - 1] = num;
        return childPath;
    }

    private Path take(int level) {
        int[] na = new int[level];
        System.arraycopy(array, 0, na, 0, level);
        return new Path(na);
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
        if (other instanceof Path) {
            return Arrays.equals(array, ((Path) other).array);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

}
