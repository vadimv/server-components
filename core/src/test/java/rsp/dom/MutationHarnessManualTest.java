package rsp.dom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import rsp.mutate.run.Mutate;
import rsp.mutate.run.Report;
import rsp.mutate.run.Verdict;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manual driver for the deterministic mutation harness (M1) — the usefulness gate.
 *
 * <p>The mutation run itself ({@link #mutating_NodesTreeDiff_is_caught_by_its_property_tests}) is
 * gated behind {@code -Dmutate.run=true}, so it never runs in the normal build. It mutates
 * {@link NodesTreeDiff} against {@code NodesTreeDiffPropertyTests} and prints the survivor report,
 * then asserts the two known-bug fixtures are caught:
 * <ul>
 *   <li>dropping {@code htmlBuilder.reset()} before building the new text in the text&harr;text
 *       comparison branch (line {@value #RESET_LINE}) is KILLED;</li>
 *   <li>forcing the keyed {@code isRetained} to {@code true} is KILLED.</li>
 * </ul>
 *
 * <p>That gate selects the reset() mutant by its source line, a magic number that silently drifts
 * whenever the file changes. {@link #reset_line_constant_still_points_at_the_htmlBuilder_reset_call}
 * is a cheap, always-on guard that fails fast when {@link #RESET_LINE} stops pointing at the call,
 * so the drift is caught in the normal build rather than after a multi-minute mutation run.
 *
 * <p>Run the gate from inside core's test JVM so the forked mutants inherit core's full test classpath:
 * {@code mvn -pl core -am test -Dtest=MutationHarnessManualTest -Dmutate.run=true}.
 */
class MutationHarnessManualTest {

    private static final int RESET_LINE = 107; // htmlBuilder.reset() before building the new text (text<->text branch)

    /** Path of the mutated source, relative to a build root, used by the line-number guard below. */
    private static final String SOURCE_PATH = "rsp/dom/NodesTreeDiff.java";

    @Test
    @EnabledIfSystemProperty(named = "mutate.run", matches = "true")
    void mutating_NodesTreeDiff_is_caught_by_its_property_tests() {
        final Report report = Mutate.run(
                "rsp.dom.NodesTreeDiff",
                List.of("rsp.dom.NodesTreeDiffPropertyTests"));

        System.out.println(report.render());

        assertEquals(Verdict.KILLED, verdictAtLine(report, "RemoveVoidCall", RESET_LINE),
                "dropping htmlBuilder.reset() at line " + RESET_LINE + " must be caught");
        assertEquals(Verdict.KILLED, report.verdictFor("MutateReturn", "isRetained"),
                "forcing isRetained to true (every keyed child retained) must be caught");
    }

    /**
     * Keeps {@link #RESET_LINE} honest: the gate above singles out the reset() mutant by this line, so
     * if an edit shifts the file the gate would otherwise fail with a cryptic "no mutant at line N"
     * after minutes of forking. This catches it instantly in the normal build. The line before and
     * after are pinned too, so the {@code reset()} earlier in the same branch (before building the old
     * text) cannot masquerade as the targeted one — only the reset() that precedes building the new
     * text matches all three.
     */
    @Test
    void reset_line_constant_still_points_at_the_htmlBuilder_reset_call() {
        final List<String> src = sourceLines();
        assertSourceLineContains(src, RESET_LINE - 1, "htmlBuilder.toString()");       // old text just captured
        assertSourceLineContains(src, RESET_LINE,     "htmlBuilder.reset()");          // the targeted mutant
        assertSourceLineContains(src, RESET_LINE + 1, "htmlBuilder.buildHtml(node2)"); // new text being built
    }

    private static Verdict verdictAtLine(final Report report, final String operatorId, final int line) {
        return report.results().stream()
                .filter(r -> r.point().operatorId().equals(operatorId) && r.point().sourceLine() == line)
                .map(Report.Result::verdict)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + operatorId + " mutant at line " + line));
    }

    /** Asserts the 1-based {@code line} of the mutated source contains {@code expected}. */
    private static void assertSourceLineContains(final List<String> src, final int line, final String expected) {
        assertTrue(line >= 1 && line <= src.size(),
                () -> "line " + line + " is out of range for " + SOURCE_PATH + " (" + src.size() + " lines)");
        final String actual = src.get(line - 1);
        assertTrue(actual.contains(expected),
                () -> "RESET_LINE (" + RESET_LINE + ") context check failed: line " + line + " of " + SOURCE_PATH
                        + " should contain \"" + expected + "\" but was: \"" + actual.strip()
                        + "\" — the source shifted; update RESET_LINE.");
    }

    /** Reads the mutated source file, locating it relative to the build root. */
    private static List<String> sourceLines() {
        final Path path = locateSource();
        try {
            return Files.readAllLines(path);
        } catch (final IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    /** Surefire runs with the module dir as the working dir; also tolerate a repo-root invocation. */
    private static Path locateSource() {
        for (final String root : List.of("src/main/java", "core/src/main/java")) {
            final Path candidate = Paths.get(root, SOURCE_PATH);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("Could not locate " + SOURCE_PATH + " from " + Paths.get("").toAbsolutePath());
    }
}
