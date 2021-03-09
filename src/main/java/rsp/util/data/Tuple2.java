package rsp.util.data;


import java.util.Objects;

/**
 * A bag of two elements of parametrized types.
 * @param <S> the first element's type
 * @param <T> the second element's type
 */
public final class Tuple2<S, T> {
    /**
     * The first element.
     */
    public final S _1;
    /**
     * The second element.
     */
    public final T _2;

    /**
     * Creates a new instance of a tuple.
     * @param _1 the first element
     * @param _2 the second element
     */
    public Tuple2(S _1, T _2) {
        this._1 = _1;
        this._2 = _2;
    }

    /**
     * Creates a new instance of a tuple.
     * @param _1 the first element
     * @param _2 the second element
     * @param <S> the first element's type
     * @param <T> the second element's type
     * @return a new instance
     */
    public static <S, T> Tuple2<S, T> of(S _1, T _2) {
        return new Tuple2<>(_1, _2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tuple2<?, ?> tuple2 = (Tuple2<?, ?>) o;
        return Objects.equals(_1, tuple2._1) &&
                Objects.equals(_2, tuple2._2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_1, _2);
    }

    @Override
    public String toString() {
        return "Tuple2{" +
                "_1=" + _1 +
                ", _2=" + _2 +
                '}';
    }
}
