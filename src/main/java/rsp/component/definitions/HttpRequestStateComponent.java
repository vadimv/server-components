package rsp.component.definitions;

import rsp.component.ComponentStateSupplier;
import rsp.server.http.HttpRequest;

import java.util.Objects;
import java.util.function.Function;

/**
 * A component with a state derived from an HTTP request.
 * @param <S> this component's state type
 */
public abstract class HttpRequestStateComponent<S> extends Component<S> {

    protected final HttpRequest httpRequest;

    public HttpRequestStateComponent(final HttpRequest httpRequest) {
        super(HttpRequestStateComponent.class);
        this.httpRequest = Objects.requireNonNull(httpRequest);
    }

    public abstract Function<HttpRequest, S> routing();

    @Override
    public ComponentStateSupplier<S> initStateSupplier() {
        final Function<HttpRequest, S> routing = routing();
        Objects.requireNonNull(routing);
        return (_,_) -> routing.apply(httpRequest);
    }

}
