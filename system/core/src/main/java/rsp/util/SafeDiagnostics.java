package rsp.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * Builds diagnostics with stack locations but without exception messages.
 * Exception messages may contain request data, credentials, file paths, or application state.
 */
public final class SafeDiagnostics {
    private static final int MAX_CAUSE_DEPTH = 8;
    private static final int MAX_FRAMES_PER_THROWABLE = 32;

    private SafeDiagnostics() {
    }

    /**
     * Describes a failed operation using exception types and bounded stack frames. Exception
     * messages and suppressed exceptions are deliberately omitted because they may contain
     * application data.
     *
     * @param operation a non-sensitive operation description
     * @param failure the failure to classify
     * @return a message suitable for default server-side logs
     */
    public static String failure(final String operation, final Throwable failure) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(failure);

        final StringBuilder diagnostic = new StringBuilder(operation)
                .append(" [failureType=")
                .append(failure.getClass().getName())
                .append(']');
        appendFrames(diagnostic, failure);

        final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(failure);
        Throwable cause = failure.getCause();
        int causeDepth = 0;
        while (cause != null && causeDepth < MAX_CAUSE_DEPTH) {
            if (!visited.add(cause)) {
                diagnostic.append("\nCaused by: <cycle>");
                return diagnostic.toString();
            }
            diagnostic.append("\nCaused by: ").append(cause.getClass().getName());
            appendFrames(diagnostic, cause);
            cause = cause.getCause();
            causeDepth++;
        }
        if (cause != null) {
            diagnostic.append("\nCaused by: <cause chain truncated>");
        }
        return diagnostic.toString();
    }

    private static void appendFrames(final StringBuilder diagnostic, final Throwable failure) {
        final StackTraceElement[] frames = failure.getStackTrace();
        final int includedFrames = Math.min(frames.length, MAX_FRAMES_PER_THROWABLE);
        for (int i = 0; i < includedFrames; i++) {
            diagnostic.append("\n    at ").append(frames[i]);
        }
        if (includedFrames < frames.length) {
            diagnostic.append("\n    ... ")
                    .append(frames.length - includedFrames)
                    .append(" frames omitted");
        }
    }
}
