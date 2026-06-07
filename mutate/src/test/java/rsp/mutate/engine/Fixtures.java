package rsp.mutate.engine;

/** Tiny fixture whose bytecode exercises each operator with observable behaviour. */
public class Fixtures {

    /** A conditional + two int returns: NegateCondition (×1) and MutateReturn (×2). */
    public static boolean flag(final int x) {
        return x > 3;
    }

    /** A void call: RemoveVoidCall (×1). */
    public static void clear(final StringBuilder sb) {
        sb.setLength(0);
    }

    /** A reference return: MutateReturn (×1, force null). */
    public static String name() {
        return "real";
    }
}
