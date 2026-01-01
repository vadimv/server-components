package rsp.compositions;

import rsp.component.ComponentContext;

public abstract class EditViewContract<T> extends ViewContract {

    protected EditViewContract(ComponentContext context) {
        super(context);
    }

    public abstract T item();
}
