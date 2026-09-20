package rsp.http;

import rsp.component.definitions.Component;
import rsp.http.routing.HttpRouteContext;

/** Creates the live root component for a matched page route. */
@FunctionalInterface
public interface PageHandler {
    Component<?, ?> handle(HttpRequest request, HttpRouteContext route);
}
