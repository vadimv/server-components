package rsp.http.routing;

import java.util.List;

/** Read-only route metadata shared by executable and server-adapted routers. */
public interface HttpRouteCatalog {
    List<HttpRouteDefinition> routeDefinitions();
}
