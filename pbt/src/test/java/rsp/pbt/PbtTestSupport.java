package rsp.pbt;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Shared helpers for the harness self-tests: deterministic config and failure capture. */
final class PbtTestSupport {

    private PbtTestSupport() {
    }

    /** Runs {@code body} with {@code pbt.seed}/{@code pbt.tries} pinned, then restores prior values. */
    static void withConfig(final long seed, final int tries, final Runnable body) {
        final String oldSeed = System.getProperty("pbt.seed");
        final String oldTries = System.getProperty("pbt.tries");
        System.setProperty("pbt.seed", Long.toString(seed));
        System.setProperty("pbt.tries", Integer.toString(tries));
        try {
            body.run();
        } finally {
            restore("pbt.seed", oldSeed);
            restore("pbt.tries", oldTries);
        }
    }

    /** Asserts {@code body} fails and returns the reported {@link AssertionError} message. */
    static String captureFailureMessage(final Runnable body) {
        return assertThrows(AssertionError.class, body::run).getMessage();
    }

    static <T> List<T> shrinkValues(final Shrinkable<T> shrinkable) {
        return shrinkable.shrinks().map(Shrinkable::value).toList();
    }

    static <T> Stream<Shrinkable<T>> shrinksOf(final Shrinkable<T> shrinkable) {
        return shrinkable.shrinks();
    }

    private static void restore(final String key, final String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
