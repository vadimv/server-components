package rsp.mutate.run;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs the covering tests against one mutant in a fresh JVM, isolating the mutation by
 * <em>classpath shadowing</em>: the mutated class is written into a temp directory placed first on
 * the forked classpath, so it shadows the original without any agent or in-process class loading.
 *
 * <p>The forked classpath is inherited from this process ({@code java.class.path}), so when the
 * runner is driven from a module's test JVM it automatically sees that module's classes,
 * test-classes, dependencies, JUnit and {@code pbt}.
 */
public final class MutationRunner {

    private final Duration timeout;

    public MutationRunner(final Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * @param mutatedClassBytes the mutated bytecode
     * @param binaryClassName   the class the bytes belong to (e.g. {@code rsp.dom.NodesTreeDiff})
     * @param testClasses       fully-qualified covering test classes to run
     * @return the {@link Verdict}
     */
    public Verdict run(final byte[] mutatedClassBytes, final String binaryClassName, final List<String> testClasses) {
        Path shadow = null;
        try {
            shadow = Files.createTempDirectory("mutate-shadow");
            final Path classFile = shadow.resolve(binaryClassName.replace('.', '/') + ".class");
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, mutatedClassBytes);

            final String classpath = shadow + File.pathSeparator + System.getProperty("java.class.path");
            final List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            command.add("-cp");
            command.add(classpath);
            command.add(ForkedTestWorker.class.getName());
            command.add(binaryClassName); // force-loaded by the fork to verify the mutant
            command.addAll(testClasses);

            final Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return Verdict.TIMEOUT;
            }
            return switch (process.exitValue()) {
                case ForkedTestWorker.SURVIVED -> Verdict.SURVIVED;
                case ForkedTestWorker.KILLED -> Verdict.KILLED;
                default -> Verdict.ERROR;
            };
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return Verdict.ERROR;
        } finally {
            deleteRecursively(shadow);
        }
    }

    private static void deleteRecursively(final Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (final IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (final IOException ignored) {
            // best-effort cleanup
        }
    }
}
