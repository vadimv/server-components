package rsp.mutate.run;

/** End-to-end fixture. {@code add}/{@code isPositive} are asserted by {@code AdderTest}; {@code record} is not. */
public class Adder {

    public static int add(final int a, final int b) {
        return a + b;
    }

    public static boolean isPositive(final int x) {
        return x > 0;
    }

    /** A void side effect that no test asserts — its RemoveVoidCall mutant should SURVIVE. */
    public static void record(final StringBuilder sb) {
        sb.setLength(0);
    }
}
