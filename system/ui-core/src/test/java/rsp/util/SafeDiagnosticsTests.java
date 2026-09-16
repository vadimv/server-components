package rsp.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeDiagnosticsTests {
    private static final String CANARY_SECRET = "diagnostic-canary-secret";

    @Test
    void failure_includes_stack_and_cause_types_but_not_messages() {
        final Throwable cause = new IllegalArgumentException("cause-" + CANARY_SECRET);
        cause.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("cause.package.Parser", "parse", "Parser.java", 19)
        });
        final Throwable failure = new IllegalStateException(CANARY_SECRET, cause);
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("app.orders.OrderPage", "render", "OrderPage.java", 42)
        });

        final String diagnostic = SafeDiagnostics.failure("Operation failed", failure);

        assertTrue(diagnostic.startsWith("Operation failed [failureType=java.lang.IllegalStateException]"));
        assertTrue(diagnostic.contains("at app.orders.OrderPage.render(OrderPage.java:42)"));
        assertTrue(diagnostic.contains("Caused by: java.lang.IllegalArgumentException"));
        assertTrue(diagnostic.contains("at cause.package.Parser.parse(Parser.java:19)"));
        assertFalse(diagnostic.contains(CANARY_SECRET));
        assertFalse(diagnostic.contains("cause-"));
    }

    @Test
    void failure_includes_all_frames_at_the_limit() {
        final Throwable failure = failureWithFrames("example.limit.Frame", 32);

        final String diagnostic = SafeDiagnostics.failure("Operation failed", failure);

        assertEquals(32, diagnostic.lines().filter(line -> line.startsWith("    at ")).count());
        assertFalse(diagnostic.contains("frames omitted"));
        assertFalse(diagnostic.contains(CANARY_SECRET));
    }

    @Test
    void failure_truncates_root_frames_beyond_the_limit() {
        final Throwable failure = failureWithFrames("example.root.Frame", 33);

        final String diagnostic = SafeDiagnostics.failure("Operation failed", failure);

        assertEquals(32, diagnostic.lines().filter(line -> line.startsWith("    at ")).count());
        assertTrue(diagnostic.contains("at example.root.Frame31.run(Frame.java:31)"));
        assertFalse(diagnostic.contains("example.root.Frame32"));
        assertTrue(diagnostic.contains("... 1 frames omitted"));
        assertFalse(diagnostic.contains(CANARY_SECRET));
    }

    @Test
    void failure_also_bounds_cause_frames() {
        final Throwable cause = failureWithFrames("example.cause.Frame", 40);
        final Throwable failure = stacklessFailure("root-" + CANARY_SECRET);
        failure.initCause(cause);

        final String diagnostic = SafeDiagnostics.failure("Operation failed", failure);

        assertEquals(32, diagnostic.lines().filter(line -> line.startsWith("    at ")).count());
        assertTrue(diagnostic.contains("at example.cause.Frame31.run(Frame.java:31)"));
        assertFalse(diagnostic.contains("example.cause.Frame32"));
        assertTrue(diagnostic.contains("... 8 frames omitted"));
        assertFalse(diagnostic.contains(CANARY_SECRET));
    }

    @Test
    void failure_truncates_a_deep_cause_chain() {
        final Throwable failure = stacklessFailure("root-" + CANARY_SECRET);
        Throwable tail = failure;
        for (int i = 0; i < 9; i++) {
            final Throwable cause = stacklessFailure("cause-" + i + '-' + CANARY_SECRET);
            tail.initCause(cause);
            tail = cause;
        }

        final String diagnostic = SafeDiagnostics.failure("Operation failed", failure);

        assertEquals(9, occurrences(diagnostic, "Caused by:"));
        assertTrue(diagnostic.endsWith("Caused by: <cause chain truncated>"));
        assertFalse(diagnostic.contains(CANARY_SECRET));
    }

    @Test
    void failure_detects_a_cyclic_cause_chain() {
        final Throwable failure = stacklessFailure("first-" + CANARY_SECRET);
        final Throwable cause = stacklessFailure("second-" + CANARY_SECRET);
        failure.initCause(cause);
        cause.initCause(failure);

        final String diagnostic = SafeDiagnostics.failure("Operation failed", failure);

        assertEquals(2, occurrences(diagnostic, "Caused by:"));
        assertTrue(diagnostic.endsWith("Caused by: <cycle>"));
        assertFalse(diagnostic.contains(CANARY_SECRET));
    }

    @Test
    void failure_handles_an_empty_stack_trace() {
        final Throwable failure = stacklessFailure(CANARY_SECRET);

        final String diagnostic = SafeDiagnostics.failure("Operation failed", failure);

        assertEquals("Operation failed [failureType=java.lang.RuntimeException]", diagnostic);
        assertFalse(diagnostic.contains(CANARY_SECRET));
    }

    @Test
    void failure_omits_suppressed_exceptions() {
        final Throwable failure = stacklessFailure("root-" + CANARY_SECRET);
        final Throwable suppressed = new IOException("suppressed-" + CANARY_SECRET);
        suppressed.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("private.suppressed.Resource", "close", "Resource.java", 7)
        });
        failure.addSuppressed(suppressed);

        final String diagnostic = SafeDiagnostics.failure("Operation failed", failure);

        assertFalse(diagnostic.contains("java.io.IOException"));
        assertFalse(diagnostic.contains("private.suppressed"));
        assertFalse(diagnostic.contains(CANARY_SECRET));
    }

    private static RuntimeException failureWithFrames(final String classNamePrefix, final int frameCount) {
        final RuntimeException failure = new RuntimeException(CANARY_SECRET);
        failure.setStackTrace(IntStream.range(0, frameCount)
                .mapToObj(i -> new StackTraceElement(classNamePrefix + i, "run", "Frame.java", i))
                .toArray(StackTraceElement[]::new));
        return failure;
    }

    private static RuntimeException stacklessFailure(final String message) {
        final RuntimeException failure = new RuntimeException(message);
        failure.setStackTrace(new StackTraceElement[0]);
        return failure;
    }

    private static int occurrences(final String value, final String search) {
        return (value.length() - value.replace(search, "").length()) / search.length();
    }
}
