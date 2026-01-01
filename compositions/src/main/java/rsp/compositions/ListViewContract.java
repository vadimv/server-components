package rsp.compositions;

import rsp.component.ComponentContext;

import java.util.List;

public abstract class ListViewContract<T> extends ViewContract {

    protected ListViewContract(ComponentContext context) {
        super(context);
    }

    public abstract int page();

    public abstract String sort();

    public abstract List<T> items();
}
