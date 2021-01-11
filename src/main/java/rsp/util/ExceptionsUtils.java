package rsp.util;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class ExceptionsUtils {
    /**
     * Reads an exception's stack trace as a {@link String}.
     * @param ex an exception
     * @return the result string
     */
    public static String stackTraceToString(Throwable ex) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }
}
