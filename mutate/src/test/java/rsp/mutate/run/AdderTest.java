package rsp.mutate.run;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link Adder#add} and {@link Adder#isPositive} but deliberately NOT {@link Adder#record},
 * so the mutation run finds exactly one survivor. Doubles as a normal unit test of {@code Adder}.
 */
class AdderTest {

    @Test
    void add_sums() {
        assertEquals(3, Adder.add(1, 2));
    }

    @Test
    void positive_is_correct() {
        assertTrue(Adder.isPositive(5));
        assertFalse(Adder.isPositive(-5));
    }
}
