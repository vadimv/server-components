package rsp.mutate.run;

import rsp.mutate.engine.MutationEngine;
import rsp.mutate.engine.MutationPoint;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates a mutation run: enumerate the target class, apply each mutation, run the covering
 * tests in a fresh JVM, and collect a {@link Report}. Run it from inside a module's test JVM (e.g. a
 * driver test) so the forked mutants inherit that module's full test classpath.
 */
public final class Mutate {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private Mutate() {
    }

    public static Report run(final String targetBinaryClassName, final List<String> testClasses) {
        return run(targetBinaryClassName, testClasses, DEFAULT_TIMEOUT);
    }

    /**
     * A short "how to run me" banner for a mutation driver test whose run method is gated behind
     * {@code -Dmutate.run=true}. A driver mutates a target class against its covering tests; it is a
     * manual, minutes-long gate (a fresh JVM is forked per mutant), so it is off by default and the
     * gated method is silently skipped in a normal build. Surface this banner in that case — e.g. from
     * a companion test annotated {@code @DisabledIfSystemProperty(named = "mutate.run", matches = "true")}
     * — so a developer who forgot the flag is told what to do instead of seeing nothing happen.
     *
     * @param mavenModule    the module path to pass to {@code -pl}, e.g. {@code system/json}
     * @param driverTestName the simple name of the driver test class, e.g. {@code JsonMutationHarnessManualTest}
     * @return a multi-line, ready-to-print help message
     */
    public static String gateHelp(final String mavenModule, final String driverTestName) {
        final String nl = System.lineSeparator();
        return nl
                + "  Mutation gate is OFF: -Dmutate.run is not \"true\", so no mutation run happened." + nl
                + "  It is a manual, minutes-long gate (a JVM is forked per mutant), not part of the normal build." + nl
                + nl
                + "  To run it:" + nl
                + "    mvn -pl " + mavenModule + " -am test -Dtest=" + driverTestName + " -Dmutate.run=true" + nl
                + nl
                + "  Optional tuning (forwarded into each mutant fork):" + nl
                + "    -Dpbt.tries=N   thoroughness of the property-based covering tests" + nl
                + "    -Dpbt.seed=N    pin the covering-test seed (a default is pinned for reproducibility)" + nl;
    }

    public static Report run(final String targetBinaryClassName, final List<String> testClasses,
                             final Duration perMutantTimeout) {
        final byte[] original = loadClassBytes(targetBinaryClassName);
        final MutationRunner runner = new MutationRunner(perMutantTimeout);

        // Baseline: the covering tests must pass (and be runnable) against the UNMUTATED class.
        // Otherwise every mutant is trivially "killed" and the report is falsely perfect. Running the
        // original bytes through the same fork path also catches a misconfigured classpath / test name.
        final Verdict baseline = runner.run(original, targetBinaryClassName, testClasses);
        if (baseline != Verdict.SURVIVED) {
            throw new BaselineFailedException(targetBinaryClassName, testClasses, baseline);
        }

        final MutationEngine engine = new MutationEngine();
        final List<MutationPoint> points = engine.enumerate(original);
        final List<Report.Result> results = new ArrayList<>(points.size());
        for (final MutationPoint point : points) {
            final byte[] mutant = engine.apply(original, point);
            results.add(new Report.Result(point, runner.run(mutant, targetBinaryClassName, testClasses)));
        }
        return new Report(targetBinaryClassName, results);
    }

    /** Reads the original compiled bytes of a class from this process's classpath. */
    private static byte[] loadClassBytes(final String binaryClassName) {
        final String resource = binaryClassName.replace('.', '/') + ".class";
        try (InputStream in = Mutate.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("class not found on classpath: " + binaryClassName);
            }
            return in.readAllBytes();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
