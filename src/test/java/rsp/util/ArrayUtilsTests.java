package rsp.util;

import org.junit.Assert;
import org.junit.Test;

public class ArrayUtilsTests {
    @Test
    public void should_concat_two_arrays_correctly() {
        final Integer[] a1 = new Integer[] {0, 1, 2, 3};
        final Integer[] a2 = new Integer[] {4, 5, 6, 7};
        final Integer[] result =  ArrayUtils.concat(a1, a2);
        Assert.assertArrayEquals(new Integer[] {0, 1, 2, 3, 4, 5, 6, 7}, result);
    }

    @Test
    public void should_append_item_to_array_correctly() {
        final Integer[] a1 = new Integer[] {0, 1, 2, 3};
        final Integer[] result =  ArrayUtils.append(a1, 4);
        Assert.assertArrayEquals(new Integer[] {0, 1, 2, 3, 4}, result);
    }
}
