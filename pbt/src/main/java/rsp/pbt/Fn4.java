package rsp.pbt;

/** A four-argument function for {@link Gen#combine(Gen, Gen, Gen, Gen, Fn4)}. */
@FunctionalInterface
public interface Fn4<A, B, C, D, R> {
    R apply(A a, B b, C c, D d);
}
