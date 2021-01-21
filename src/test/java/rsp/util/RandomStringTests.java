package rsp.util;

import org.junit.Assert;
import org.junit.Test;

public class RandomStringTests {
    @Test
    public void should_create_random_strings() {
        final int stringLength = 16;
        final RandomString rs = new RandomString(stringLength);

        final String s1 = rs.newString();
        Assert.assertEquals(stringLength, s1.length());

        final String s2 = rs.newString();
        Assert.assertEquals(stringLength, s2.length());

        Assert.assertNotEquals(s1, s2);
    }

}
