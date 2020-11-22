package rsp.util;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionsUtils {
    public static String stackTraceToString(Throwable ex) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }
}
