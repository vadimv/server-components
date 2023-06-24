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
    public Tuple2(final S _1, final T _2) {
        this._1 = Objects.requireNonNull(_1);
        this._2 = Objects.requireNonNull(_2);
    }

    /**
     * Creates a new instance of a tuple.
     * @param _1 the first element
     * @param _2 the second element
     * @param <S> the first element's type
     * @param <T> the second element's type
     * @return a new instance
     */
    public static <S, T> Tuple2<S, T> of(final S _1, final T _2) {
        return new Tuple2<>(_1, _2);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Tuple2<?, ?> tuple2 = (Tuple2<?, ?>) o;
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
