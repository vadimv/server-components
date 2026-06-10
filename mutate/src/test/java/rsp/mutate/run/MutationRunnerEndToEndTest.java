package rsp.mutate.run;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the full pipeline (enumerate → apply → fork-and-shadow → run JUnit) against the {@link Adder}
 * fixture: tested mutants are KILLED, the untested side effect SURVIVES. Validates fork + classpath
 * shadowing + Minion without depending on any other module.
 */
class MutationRunnerEndToEndTest {

    @Test
    void pipeline_kills_tested_mutants_and_surfaces_the_untested_one() {
        final Report report = Mutate.run(
                "rsp.mutate.run.Adder",
                List.of("rsp.mutate.run.AdderTest"),
                Duration.ofSeconds(60));

        assertEquals(Verdict.KILLED, report.verdictFor("MutateReturn", "add"),
                "forcing add's return is caught by add_sums");
        assertEquals(Verdict.KILLED, report.verdictFor("NegateCondition", "isPositive"),
                "negating isPositive's guard is caught by positive_is_correct");
        assertEquals(Verdict.SURVIVED, report.verdictFor("RemoveVoidCall", "record"),
                "dropping the untested setLength side effect survives");

        assertTrue(report.survivors().size() >= 1, "at least the record() gap");
    }

    @Test
    void aborts_when_the_baseline_suite_is_not_green() {
        // BrokenAdderProbe fails against the unmutated Adder, so the baseline is red — without this
        // guard every mutant would be trivially "killed" and the report falsely perfect.
        final BaselineFailedException ex = assertThrows(BaselineFailedException.class,
                () -> Mutate.run("rsp.mutate.run.Adder", List.of("rsp.mutate.run.BrokenAdderProbe"),
                        Duration.ofSeconds(60)));
        assertTrue(ex.getMessage().contains("Baseline is not green"), ex.getMessage());
    }
}
