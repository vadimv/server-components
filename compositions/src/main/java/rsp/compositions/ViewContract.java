package rsp.compositions;

import rsp.component.ComponentContext;

import java.util.Optional;

public abstract class ViewContract {
    
    protected ComponentContext context;

    public void setContext(ComponentContext context) {
        this.context = context;
    }

    protected <T> T resolve(QueryParam<T> param) {
        return param.resolve(context);
    }
    
    public abstract String name();
}
