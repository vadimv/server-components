package rsp.compositions;


public abstract class ListViewContract<T> extends ViewContract {
    public abstract int page();

    public abstract String sort();
}
