package rsp.mutate.run;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
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

    @Test
    void unverifiable_mutant_is_an_error_even_when_no_test_loads_the_target() {
        // AdderTest never references Phantom, so without the fork force-loading the target this
        // invalid mutant would never be loaded and would be falsely reported as SURVIVED.
        final byte[] invalid = unverifiableClass("rsp.mutate.run.Phantom");
        final Verdict verdict = new MutationRunner(Duration.ofSeconds(30))
                .run(invalid, "rsp.mutate.run.Phantom", List.of("rsp.mutate.run.AdderTest"));
        assertEquals(Verdict.ERROR, verdict, "an unverifiable mutant must be ERROR, never SURVIVED");
    }

    /**
     * Bytes for a class with a method that pops an empty operand stack — rejected by the JVM verifier
     * at link time. {@code DROP_STACK_MAPS} stops the Class-File API from validating the stack itself,
     * so the malformed bytecode is emitted and only the JVM rejects it (which is the point).
     */
    private static byte[] unverifiableClass(final String binaryName) {
        return ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS).build(ClassDesc.of(binaryName), cb ->
                cb.withMethodBody("boom", MethodTypeDesc.of(ConstantDescs.CD_void),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        code -> code.pop().return_()));
    }
}
