package rsp.util.json;



import org.junit.jupiter.api.Test;
import rsp.util.ExceptionsUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExceptionsUtilsTests {
    @Test
    public void should_get_stack_trace_correctly() {
        final Throwable t = new Throwable();
        t.setStackTrace(new StackTraceElement[] {
             new StackTraceElement("class0", "method0", "file0", 16)
        });
        final String current = ExceptionsUtils.stackTraceToString(t);
        assertTrue(current.contains("java.lang.Throwable"));
        assertTrue(current.contains("class0.method0(file0:16)"));
    }
}
