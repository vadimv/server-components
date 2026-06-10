package rsp.mutate.run;

import java.util.List;

/**
 * Thrown when the covering tests do not pass (and run cleanly) against the <em>unmutated</em> class.
 * Without a green baseline every mutant is trivially "killed", so the report would be falsely
 * perfect — the run is aborted instead.
 */
public final class BaselineFailedException extends IllegalStateException {

    public BaselineFailedException(final String target, final List<String> testClasses, final Verdict baseline) {
        super(message(target, testClasses, baseline));
    }

    private static String message(final String target, final List<String> testClasses, final Verdict baseline) {
        final String why = switch (baseline) {
            case KILLED -> "a covering test already fails against the unmutated class";
            case ERROR -> "the covering tests could not be discovered or run (check the test class names and classpath)";
            case TIMEOUT -> "the covering tests exceeded the per-mutant timeout";
            case SURVIVED -> "all tests passed"; // not reached
        };
        return "Baseline is not green for " + target + " against " + testClasses + ": " + baseline
                + " — " + why + ". Mutation results would be meaningless (every mutant trivially 'killed'); aborting.";
    }
}
