package rsp.http;

import rsp.http.routing.HttpRouteContext;

/** Synchronous handler returning either a transport response or a UI page result. */
@FunctionalInterface
public interface RouteHandler {
    HttpResult handle(HttpRequest request, HttpRouteContext route);
}
