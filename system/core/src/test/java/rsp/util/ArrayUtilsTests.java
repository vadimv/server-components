package rsp.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ArrayUtilsTests {
    @Test
    void should_concat_two_arrays_correctly() {
        final Integer[] a1 = new Integer[] {0, 1, 2, 3};
        final Integer[] a2 = new Integer[] {4, 5, 6, 7};
        final Integer[] result =  ArrayUtils.concat(a1, a2);
        assertArrayEquals(new Integer[] {0, 1, 2, 3, 4, 5, 6, 7}, result);
    }

    @Test
    void should_append_item_to_array_correctly() {
        final Integer[] a1 = new Integer[] {0, 1, 2, 3};
        final Integer[] result =  ArrayUtils.append(a1, 4);
        assertArrayEquals(new Integer[] {0, 1, 2, 3, 4}, result);
    }
}
