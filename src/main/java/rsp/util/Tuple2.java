package rsp.util;


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
}
