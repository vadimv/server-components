package rsp.util.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import rsp.mutate.run.Mutate;
import rsp.mutate.run.Report;
import rsp.mutate.run.Verdict;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manual driver for the deterministic mutation harness (M1) over the JSON parser — the usefulness gate.
 *
 * <p>The mutation run ({@link #mutating_the_json_parser_is_caught_by_its_tests}) is gated behind
 * {@code -Dmutate.run=true}, so it never runs in the normal build. It mutates the concrete
 * recursive-descent parser ({@code Json.Parser}) against the parser's covering tests, prints the
 * survivor report, then asserts a couple of high-confidence mutants are caught.
 *
 * <p>Run it from inside json's test JVM so the forked mutants inherit json's full test classpath:
 * {@code mvn -pl system/json -am test -Dtest=JsonMutationHarnessManualTest -Dmutate.run=true}.
 * Add {@code -Dpbt.tries=N} to tune how hard the property-based covering tests try inside each fork.
 *
 * <p>When the gate is off, {@link #explains_how_to_run_the_gate_when_skipped} prints that command so a
 * skipped run is not silent.
 */
class JsonMutationHarnessManualTest {

    /**
     * The mutation target: {@code Json}'s private nested {@code Parser} — the class that actually does
     * the parsing — addressed by its binary name ({@code $} for the nested class).
     */
    private static final String TARGET = "rsp.util.json.Json$Parser";

    /**
     * Covering tests. The round-trip properties exercise the happy path; the direct parser and security
     * tests drive the error and limit branches the round-trip never reaches. A broad, passing baseline
     * is what makes a SURVIVED verdict meaningful rather than merely "not covered".
     */
    private static final List<String> COVERING_TESTS = List.of(
            "rsp.util.json.JsonPropertyTests",
            "rsp.util.json.JsonParserTests",
            "rsp.util.json.JsonSecurityTests");

    @Test
    @EnabledIfSystemProperty(named = "mutate.run", matches = "true",
            disabledReason = "manual mutation gate; run with -Dtest=JsonMutationHarnessManualTest -Dmutate.run=true")
    void mutating_the_json_parser_is_caught_by_its_tests() {
        final Report report = Mutate.run(TARGET, COVERING_TESTS);

        System.out.println(report.render());

        // Reaching here already proves the baseline passed (Mutate.run throws BaselineFailedException
        // otherwise), i.e. the classpath and test names are right and the tests pass unmutated.
        assertTrue(report.results().size() > 0, "the engine should find mutation points in " + TARGET);
        assertTrue(report.count(Verdict.KILLED) > 0, "the covering tests should catch at least some mutants");

        // Known-bug fixtures — high-confidence mutants the covering tests must catch. Negating the
        // "unexpected trailing content" guard in parseDocument makes the parser accept junk after a
        // value, which the round-trip property immediately exposes.
        assertEquals(Verdict.KILLED, report.verdictFor("NegateCondition", "parseDocument"),
                "accepting trailing content after the top-level value must be caught");
    }

    /**
     * Always-on breadcrumb: when the gate above is skipped (no {@code -Dmutate.run=true}) this prints the
     * command to actually run it, so "nothing happened" is explained rather than silent. Disabled while
     * the gate is on, so it stays out of the way during a real mutation run.
     */
    @Test
    @DisabledIfSystemProperty(named = "mutate.run", matches = "true")
    void explains_how_to_run_the_gate_when_skipped() {
        System.out.println(Mutate.gateHelp("system/json", getClass().getSimpleName()));
    }
}
