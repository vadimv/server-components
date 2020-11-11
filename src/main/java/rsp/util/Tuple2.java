package rsp.util;


import java.util.Objects;

public class Tuple2<S,T> {
    public final S _1;
    public final T _2;
    
    public Tuple2(S _1, T _2) {
        this._1 = _1;
        this._2 = _2;
    }
    
    public static <S, T> Tuple2 of(S _1, T _2) {
        return new Tuple2(_1, _2);
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
