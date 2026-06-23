package rsp.mutate.run;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Intentionally fails against the real {@link Adder} — the fixture for the baseline-not-green guard.
 * Named without a {@code Test} suffix so Surefire does not run it as part of the module build; the
 * harness selects it explicitly by class name.
 */
class BrokenAdderProbe {

    @Test
    void wrong_expectation() {
        assertEquals(5, Adder.add(1, 2)); // 3 != 5 → red against the unmutated Adder
    }
}
