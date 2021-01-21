package rsp.util.json;

import org.junit.Assert;
import org.junit.Test;
import rsp.util.ExceptionsUtils;

public class ExceptionsUtilsTests {
    @Test
    public void should_get_stack_trace_correctly() {
        final Throwable t = new Throwable();
        t.setStackTrace(new StackTraceElement[] {
             new StackTraceElement("class0", "method0", "file0", 16)
        });
        final String current = ExceptionsUtils.stackTraceToString(t);
        Assert.assertTrue(current.contains("java.lang.Throwable"));
        Assert.assertTrue(current.contains("class0.method0(file0:16)"));
    }
}
