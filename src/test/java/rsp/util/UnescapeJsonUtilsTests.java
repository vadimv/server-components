package rsp.util;

import org.junit.Assert;
import org.junit.Test;

public class UnescapeJsonUtilsTests {
    @Test
    public void should_correctly_unescape_string() {
        final String result = UnescapeJsonUtils.unescape("123 abc XYZ \\n \\u262F");
        Assert.assertEquals("123 abc XYZ \n ☯", result);
    }
}
