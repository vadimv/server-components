package rsp.mutate.run;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
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
 * <p>The mutated target is linked and verified <em>before</em> the tests run, independently of whether
 * any test references it — otherwise an unverifiable mutant the selected tests never load would be
 * falsely reported as a survivor. Verification is done on a throwaway class loader so the tests still
 * load and initialise the real target lazily, at their natural time (we never force-initialise the
 * class the tests use).
 *
 * <p>This is the one place the {@code mutate} module touches the JUnit Platform; the engine does not.
 */
public final class ForkedTestWorker {

    static final int SURVIVED = 0;
    static final int KILLED = 1;
    static final int ERROR = 2;

    private ForkedTestWorker() {
    }

    static void main(final String[] args) {
        final String targetClass = args[0];
        final List<String> testClasses = Arrays.asList(args).subList(1, args.length);

        if (!verifies(targetClass)) {
            System.exit(ERROR);
        }
        System.exit(runTests(testClasses));
    }

    /**
     * Verifies the (mutated) target regardless of test coverage. On the JDK used by this project,
     * {@code Class.forName(name, false, loader)} does not reject the malformed-method fixture in the
     * runner tests, so this initialises a copy in a throwaway child loader. The tests still load the
     * real target on the system loader, lazily. Any {@code <clinit>} side effects from this probe are
     * isolated from the test class, but they still happen in the worker process — an intentional M1
     * trade-off for catching unverifiable mutants that no selected test loads.
     */
    private static boolean verifies(final String targetClass) {
        final String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
        final URL[] urls = new URL[entries.length];
        try {
            for (int i = 0; i < entries.length; i++) {
                urls[i] = Path.of(entries[i]).toUri().toURL();
            }
            try (URLClassLoader isolated = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
                Class.forName(targetClass, true, isolated);
                return true;
            }
        } catch (final Throwable t) {
            return false; // invalid mutant (VerifyError/LinkageError), or the class could not be loaded
        }
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
