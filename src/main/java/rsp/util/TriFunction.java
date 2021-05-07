package rsp.util;

public interface TriFunction<T1, T2, T3, S> {
    S apply(T1 v1, T2 v2, T3 v3);
}
