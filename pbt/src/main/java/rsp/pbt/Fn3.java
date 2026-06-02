package rsp.pbt;

/** A three-argument function, for {@link Gen#combine(Gen, Gen, Gen, Fn3)}. */
@FunctionalInterface
public interface Fn3<A, B, C, R> {
    R apply(A a, B b, C c);
}
