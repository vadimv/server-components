package rsp.util;

import java.util.Arrays;

/**
 * Provides utility methods for Java arrays.
 */
public final class ArrayUtils {

    /**
     * Concatenates two arrays into a new one.
     * @param first an array
     * @param second another array
     * @param <T> the array's type
     * @return the result array
     */
    public static <T> T[] concat(T[] first, T[] second) {
        final T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    /**
     * Appends an item to an array resulting in a new array.
     * @param array an array
     * @param item to append
     * @param <T> the array's and the item's type
     * @return the result array
     */
    public static <T> T[] append(T[] array, T item) {
        final T[] a = Arrays.copyOf(array, array.length + 1);
        a[a.length - 1] = item;
        return a;
    }
 }
