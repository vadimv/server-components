package rsp.mutate.run;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.util.Arrays;
import java.util.List;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

/**
 * The forked-JVM entry point. {@code args[0]} is the target class (whose mutated version shadows the
 * original on this JVM's classpath); {@code args[1..]} are the covering test classes. Reports via
 * exit code: {@code 0} all passed (the mutant survived), {@code 1} a failure (the mutant was killed),
 * {@code 2} nothing ran or a load/verify/discovery error occurred.
 *
 * <p>The target is force-loaded (linked and verified) <em>before</em> the tests run, independently of
 * whether any test references it — otherwise an unverifiable mutant the selected tests never load
 * would be falsely reported as a survivor.
 *
 * <p>This is the one place the {@code mutate} module touches the JUnit Platform; the engine does not.
 */
public final class ForkedTestWorker {

    static final int SURVIVED = 0;
    static final int KILLED = 1;
    static final int ERROR = 2;

    private ForkedTestWorker() {
    }

    public static void main(final String[] args) {
        final String targetClass = args[0];
        final List<String> testClasses = Arrays.asList(args).subList(1, args.length);

        // Force the (mutated) target to load, link and verify regardless of test coverage. An invalid
        // mutant throws here (VerifyError/LinkageError) and is classified as an error, never SURVIVED.
        try {
            Class.forName(targetClass);
        } catch (final Throwable t) {
            System.exit(ERROR);
        }
        System.exit(runTests(testClasses));
    }

    private static int runTests(final List<String> testClasses) {
        try {
            final LauncherDiscoveryRequest discovery = request()
                    .selectors(testClasses.stream().map(name -> selectClass(name)).toList())
                    .build();
            final Launcher launcher = LauncherFactory.create();
            final SummaryGeneratingListener listener = new SummaryGeneratingListener();
            launcher.execute(discovery, listener);

            final TestExecutionSummary summary = listener.getSummary();
            if (summary.getTestsStartedCount() == 0) {
                return ERROR; // misconfiguration: no covering tests actually ran
            }
            return summary.getTotalFailureCount() > 0 ? KILLED : SURVIVED;
        } catch (final Throwable t) {
            // VerifyError/LinkageError from an invalid mutant, or a discovery failure.
            return ERROR;
        }
    }
}
