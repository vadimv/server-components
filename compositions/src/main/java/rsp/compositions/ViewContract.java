package rsp.compositions;

import rsp.component.ComponentContext;

import java.util.Optional;

public abstract class ViewContract {

    protected final ComponentContext context;

    protected ViewContract(ComponentContext context) {
        this.context = context;
    }

    protected <T> T resolve(QueryParam<T> param) {
        return param.resolve(context);
    }

    public abstract String name();
}
