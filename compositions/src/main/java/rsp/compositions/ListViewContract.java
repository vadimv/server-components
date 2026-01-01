package rsp.compositions;

import java.util.List;

public abstract class ListViewContract<T> extends ViewContract {
    public abstract int page();

    public abstract String sort();

    public abstract List<T> items();
}
