package rsp.util;

import org.junit.Assert;
import org.junit.Test;
import rsp.util.json.JsonUtils;

public final class UnescapeJsonUtilsTests {

    @Test
    public void should_correctly_escape_string() {
        final String result = JsonUtils.escape("123 abc XYZ \n \t ☯" );
        Assert.assertEquals("123 abc XYZ \\n \\t \\u262f", result);
    }

    @Test
    public void should_correctly_escape_empty_string() {
        final String result = JsonUtils.escape("" );
        Assert.assertEquals("", result);
    }

    @Test
    public void should_correctly_unescape_string() {
        final String result = JsonUtils.unescape("123 abc XYZ \\n \\t \\u262f");
        Assert.assertEquals("123 abc XYZ \n \t ☯", result);
    }

    @Test
    public void should_correctly_unescape_empty_string() {
        final String result = JsonUtils.unescape("");
        Assert.assertEquals("", result);
    }
}
