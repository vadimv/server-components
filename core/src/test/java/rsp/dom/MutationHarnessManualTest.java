package rsp.dom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import rsp.mutate.run.Mutate;
import rsp.mutate.run.Report;
import rsp.mutate.run.Verdict;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Manual driver for the deterministic mutation harness (M1) — the usefulness gate.
 *
 * <p>Runs only with {@code -Dmutate.run=true}, so it never runs in the normal build. It mutates
 * {@link NodesTreeDiff} against {@code NodesTreeDiffPropertyTests} and prints the survivor report,
 * then asserts the two known-bug fixtures are caught:
 * <ul>
 *   <li>dropping {@code htmlBuilder.reset()} before building the new text in the text&harr;text
 *       comparison branch (line {@value #RESET_LINE}) is KILLED;</li>
 *   <li>forcing the keyed {@code isRetained} to {@code true} is KILLED.</li>
 * </ul>
 *
 * <p>Run from inside core's test JVM so the forked mutants inherit core's full test classpath:
 * {@code mvn -pl core -am test -Dtest=MutationHarnessManualTest -Dmutate.run=true}.
 */
@EnabledIfSystemProperty(named = "mutate.run", matches = "true")
class MutationHarnessManualTest {

    private static final int RESET_LINE = 107; // htmlBuilder.reset() before building the new text (text<->text branch)

    @Test
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

    private static Verdict verdictAtLine(final Report report, final String operatorId, final int line) {
        return report.results().stream()
                .filter(r -> r.point().operatorId().equals(operatorId) && r.point().sourceLine() == line)
                .map(Report.Result::verdict)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + operatorId + " mutant at line " + line));
    }
}
