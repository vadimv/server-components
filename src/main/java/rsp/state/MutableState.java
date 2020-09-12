package rsp.state;

public class MutableState<S> implements UseState<S> {
    private volatile S state;

    public MutableState(S initialState)  {
        this.state = initialState;
    }

    @Override
    public S get() {
        return state;
    }

    @Override
    public void accept(S state) {
        this.state = state;
    }
}
