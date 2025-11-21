package rsp.component.definitions;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.View;
import rsp.routing.Routing;
import rsp.server.http.HttpRequest;

import java.util.Objects;
import java.util.function.Function;

public class HttpRequestStateComponentDefinition<S> extends StatefulComponentDefinition<S> {

    private final Function<HttpRequest, S> initialStateRouting;
    private final ComponentView<S> componentView;
    private final HttpRequest httpRequest;

    public HttpRequestStateComponentDefinition(final HttpRequest httpRequest,
                                               final Function<HttpRequest, S> initialStateRouting,
                                               final ComponentView<S> componentView) {
        super(HttpRequestStateComponentDefinition.class);
        this.httpRequest = Objects.requireNonNull(httpRequest);
        this.initialStateRouting = Objects.requireNonNull(initialStateRouting);
        this.componentView = Objects.requireNonNull(componentView);
    }

    public HttpRequestStateComponentDefinition(final HttpRequest httpRequest,
                                               final Routing<HttpRequest, S> routing,
                                               final View<S> view) {
        this(httpRequest,
             routing,
             asComponentView(view));

    }

    private static <S> ComponentView<S> asComponentView(final View<S> view) {
        return _ -> view;
    }


    @Override
    public ComponentStateSupplier<S> initStateSupplier() {
        return (_,_) -> initialStateRouting.apply(httpRequest);
    }

    @Override
    public ComponentView<S> componentView() {
        return componentView;
    }
}
