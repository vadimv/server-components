package rsp.component;

import java.util.Arrays;
import java.util.Optional;

public final class ComponentPath {

    public static final ComponentPath ROOT_COMPONENT_PATH = ComponentPath.of("1");
    public static final String SEPARATOR = "_";

    private final int[] array;

    public ComponentPath(final int... xs) {
        array = xs;
    }

    public static ComponentPath of(final String path) {
        return new ComponentPath(Arrays.stream(path.split(SEPARATOR)).mapToInt(Integer::parseInt).toArray());
    }

    public int level() {
        return array.length;
    }

    public ComponentPath incLevel() {
        final int[] a = Arrays.copyOf(array, array.length + 1);
        a[a.length - 1] = 1;
        return new ComponentPath(a);
    }

    public ComponentPath incSibling() {
        final int[] a = Arrays.copyOf(array, array.length);
        a[a.length - 1]++;
        return new ComponentPath(a);
    }

    public Optional<ComponentPath> parent() {
        return level() > 1 ? Optional.of(take(array.length - 1)) : Optional.empty();
    }

    public ComponentPath addChild(final int num) {
        final ComponentPath childPath = incLevel();
        childPath.array[childPath.level() - 1] = num;
        return childPath;
    }

    private ComponentPath take(final int level) {
        final int[] na = new int[level];
        System.arraycopy(array, 0, na, 0, level);
        return new ComponentPath(na);
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
        if (other instanceof ComponentPath) {
            return Arrays.equals(array, ((ComponentPath) other).array);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

}
