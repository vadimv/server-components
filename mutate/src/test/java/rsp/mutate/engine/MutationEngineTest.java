package rsp.mutate.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutationEngineTest {

    private final MutationEngine engine = new MutationEngine();

    @Test
    void enumerate_is_deterministic_and_finds_each_operator() {
        final byte[] bytes = fixtureBytes();
        final List<MutationPoint> first = engine.enumerate(bytes);
        final List<MutationPoint> second = engine.enumerate(bytes);

        assertEquals(first, second, "enumeration must be reproducible");
        assertTrue(has(first, "RemoveVoidCall", "clear"), "RemoveVoidCall on clear");
        assertTrue(has(first, "NegateCondition", "flag"), "NegateCondition on flag");
        assertTrue(has(first, "MutateReturn", "name"), "MutateReturn on name");
        assertTrue(has(first, "MutateReturn", "flag"), "MutateReturn on flag's int return");
        // Constructor super() must not be a RemoveVoidCall site.
        assertFalse(has(first, "RemoveVoidCall", "<init>"), "constructor chaining must be skipped");
    }

    @Test
    void remove_void_call_changes_behaviour_and_loads() throws Exception {
        final Method clear = mutated("RemoveVoidCall", "clear").getDeclaredMethod("clear", StringBuilder.class);
        final StringBuilder sb = new StringBuilder("abc");
        clear.invoke(null, sb);
        assertEquals("abc", sb.toString(), "dropped setLength must leave the buffer untouched");
    }

    @Test
    void negate_condition_changes_behaviour_and_loads() throws Exception {
        final Method flag = mutated("NegateCondition", "flag").getDeclaredMethod("flag", int.class);
        assertEquals(Boolean.TRUE, flag.invoke(null, 0), "negated guard flips flag(0) from false to true");
    }

    @Test
    void mutate_return_forces_null_and_loads() throws Exception {
        final Method name = mutated("MutateReturn", "name").getDeclaredMethod("name");
        assertNull(name.invoke(null), "reference return forced to null");
    }

    // --- helpers ---

    /** Applies the first point matching (operator, method) and defines the mutated class in a fresh loader. */
    private Class<?> mutated(final String operatorId, final String methodName) {
        final byte[] original = fixtureBytes();
        final MutationPoint point = engine.enumerate(original).stream()
                .filter(p -> p.operatorId().equals(operatorId) && p.methodName().equals(methodName))
                .findFirst()
                .orElseThrow();
        final byte[] bytes = engine.apply(original, point);
        return new ByteClassLoader(Fixtures.class.getClassLoader()).define(Fixtures.class.getName(), bytes);
    }

    private static boolean has(final List<MutationPoint> points, final String operatorId, final String methodName) {
        return count(points, operatorId, methodName) > 0;
    }

    private static long count(final List<MutationPoint> points, final String operatorId, final String methodName) {
        return points.stream().filter(p -> p.operatorId().equals(operatorId) && p.methodName().equals(methodName)).count();
    }

    private static byte[] fixtureBytes() {
        try (InputStream in = Fixtures.class.getResourceAsStream("Fixtures.class")) {
            return in.readAllBytes();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Defines a class from raw bytes; a VerifyError here means a mutant produced invalid bytecode. */
    private static final class ByteClassLoader extends ClassLoader {
        ByteClassLoader(final ClassLoader parent) {
            super(parent);
        }

        Class<?> define(final String name, final byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
