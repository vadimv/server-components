package rsp.app.rest;

import rsp.http.HttpApplication;
import rsp.http.HttpMethod;
import rsp.http.HttpMiddleware;
import rsp.http.HttpStatus;
import rsp.http.json.JsonHttp;
import rsp.http.middleware.AccessLogMiddleware;
import rsp.http.middleware.CorsMiddleware;
import rsp.http.middleware.CorsPolicy;
import rsp.http.middleware.RequestIdMiddleware;
import rsp.http.middleware.SecurityHeadersMiddleware;
import rsp.http.middleware.ServerErrorMiddleware;
import rsp.http.openapi.OpenApiDocument;
import rsp.http.openapi.OpenApiInfo;
import rsp.http.openapi.OpenApiOperation;
import rsp.http.openapi.OpenApiSchema;
import rsp.http.rest.RestRouteHandler;
import rsp.http.routing.HttpRouter;
import rsp.server.socket.SocketWebServer;
import rsp.util.json.Json;
import rsp.util.json.JsonCodec;

/** Minimal REST-only application; it has no dependency on UI components or compositions. */
public final class RestHello {
    private static final JsonCodec<Message> MESSAGE_CODEC = JsonCodec.of(
            value -> new Message(Json.requireObject(value).requiredString("message")),
            message -> Json.object().put("message", message.value()));
    private static final OpenApiSchema MESSAGE_SCHEMA = OpenApiSchema.object()
            .requiredProperty("message", OpenApiSchema.string())
            .additionalProperties(false);

    private RestHello() {
    }

    public static void main(String[] args) {
        HttpApplication production = HttpMiddleware.pipeline(routedApplication(),
                new RequestIdMiddleware(),
                AccessLogMiddleware.systemLogger(System.getLogger(RestHello.class.getName())),
                SecurityHeadersMiddleware.defaults(),
                new CorsMiddleware(corsPolicy()),
                ServerErrorMiddleware.systemLogger(System.getLogger(RestHello.class.getName())));
        SocketWebServer server = new SocketWebServer(8080, production);
        server.start();
        server.join();
    }

    static HttpApplication application() {
        return HttpMiddleware.pipeline(routedApplication(),
                new RequestIdMiddleware(),
                SecurityHeadersMiddleware.defaults(),
                new CorsMiddleware(corsPolicy()),
                new ServerErrorMiddleware());
    }

    private static HttpRouter routedApplication() {
        HttpRouter api = HttpRouter.builder()
                .get("/api/hello/{name}", RestRouteHandler.sync((_, route) -> JsonHttp.response(
                                Json.object().put("message", "Hello, " + route.requiredParameter("name")))),
                        OpenApiOperation.builder()
                                .operationId("hello")
                                .summary("Return a greeting")
                                .tag("messages")
                                .pathParameter("name", OpenApiSchema.string())
                                .jsonResponse(200, "Greeting", OpenApiSchema.ref("Message"))
                                .build())
                .post("/api/echo", RestRouteHandler.jsonSync(MESSAGE_CODEC, (_, _, message) ->
                                JsonHttp.response(HttpStatus.CREATED, message, MESSAGE_CODEC)),
                        OpenApiOperation.builder()
                                .operationId("echoMessage")
                                .summary("Echo a JSON message")
                                .tag("messages")
                                .jsonRequestBody(OpenApiSchema.ref("Message"), true)
                                .jsonResponse(201, "Echoed message", OpenApiSchema.ref("Message"))
                                .response(400, "Invalid JSON")
                                .response(415, "Unsupported media type")
                                .build())
                .build();
        OpenApiDocument openApi = OpenApiDocument.builder(
                        new OpenApiInfo("RSP REST example", "1.0.0"), api)
                .componentSchema("Message", MESSAGE_SCHEMA)
                .build();
        return HttpRouter.builder()
                .include(api)
                .get("/openapi.json", openApi.handler())
                .build();
    }

    private static CorsPolicy corsPolicy() {
        return CorsPolicy.builder()
                .allowAnyOrigin()
                .allowMethods(HttpMethod.GET, HttpMethod.POST)
                .allowHeaders("Content-Type")
                .exposeHeaders(RequestIdMiddleware.HEADER_NAME)
                .build();
    }

    private record Message(String value) {
    }
}
