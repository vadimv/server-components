package rsp.util;

import java.util.Arrays;

public class ArrayUtils {

    public static <T> T[] concat(T[] first, T[] second) {
        final T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static <T> T[] append(T[] array, T item) {
        final T[] a = Arrays.copyOf(array, array.length + 1);
        a[a.length - 1] = item;
        return a;
    }
 }
