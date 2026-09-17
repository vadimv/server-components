package rsp.app.rest;

import rsp.http.HttpStatus;
import rsp.http.json.JsonHttp;
import rsp.http.json.JsonHttpException;
import rsp.http.routing.HttpRouteHandler;
import rsp.http.routing.HttpRouter;
import rsp.server.jdk.JdkWebServer;
import rsp.util.json.JsonDataType;

/** Minimal REST-only application; it has no dependency on UI components or compositions. */
public final class RestHello {
    private RestHello() {
    }

    public static void main(String[] args) {
        HttpRouter application = HttpRouter.builder()
                .get("/api/hello/{name}", HttpRouteHandler.sync((_, route) -> JsonHttp.response(
                        new JsonDataType.Object().put("message",
                                new JsonDataType.String("Hello, " + route.requiredParameter("name"))))))
                .post("/api/echo", HttpRouteHandler.sync((request, _) -> {
                    try {
                        return JsonHttp.response(HttpStatus.CREATED, JsonHttp.read(request));
                    } catch (JsonHttpException failure) {
                        return JsonHttp.error(failure);
                    }
                }))
                .build();

        JdkWebServer server = new JdkWebServer(8080, application);
        server.start();
        server.join();
    }
}
