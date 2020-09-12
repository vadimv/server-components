package rsp.dom;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public class Id {
    public static final String SEPARATOR = "_";

    private int[] array;

    public Id(int... xs) {
        array = xs;
    }

    public static Id of(String str) {
        return new Id(Arrays.stream(str.split(SEPARATOR)).mapToInt(t -> Integer.parseInt(t)).toArray());
    }

    public int level() {
        return array.length;
    }

    public Optional<Id> parent() {
        if (array.length <= 1) {
            return Optional.empty();
        } else {
            return Optional.of(take(array.length - 1));
        }
    }

    public Id take(int level) {
        int[] na = new int[level];
        System.arraycopy(array, 0, na, 0, level);
        return new Id(na);
    }

    @Override
    public String toString() {
        if(array.length == 0) {
            return "";
        } else {
            return String.join(SEPARATOR, Arrays.stream(array).mapToObj(Integer::toString).collect(Collectors.toList()));
        }
    }

    @Override
    public boolean equals(Object other) {
        if(other instanceof Id) {
            Arrays.equals(array, (int[]) other);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

}
