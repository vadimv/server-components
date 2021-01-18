package rsp.util;

import org.junit.Assert;
import org.junit.Test;

public class RandomStringTests {
    @Test
    public void should_create_random_string() {
        final String s = new RandomString(16).newString();
        Assert.assertEquals(16, s.length());
    }

}
