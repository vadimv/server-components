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
 * The forked-JVM entry point. Runs the given test classes (args) against whatever is on its
 * classpath — where the mutated class shadows the original — and reports via exit code:
 * {@code 0} all passed (the mutant survived), {@code 1} a failure (the mutant was killed),
 * {@code 2} nothing ran or an error/verify failure occurred.
 *
 * <p>This is the one place the {@code mutate} module touches the JUnit Platform; the engine does not.
 */
public final class Minion {

    static final int SURVIVED = 0;
    static final int KILLED = 1;
    static final int ERROR = 2;

    private Minion() {
    }

    public static void main(final String[] args) {
        System.exit(runTests(Arrays.asList(args)));
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
