package rsp.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RandomStringTests {
    @Test
    public void should_create_random_strings() {
        final int stringLength = 16;
        final RandomString rs = new RandomString(stringLength);

        final String s1 = rs.newString();
        Assertions.assertEquals(stringLength, s1.length());

        final String s2 = rs.newString();
        Assertions.assertEquals(stringLength, s2.length());

        Assertions.assertNotEquals(s1, s2);
    }

}
