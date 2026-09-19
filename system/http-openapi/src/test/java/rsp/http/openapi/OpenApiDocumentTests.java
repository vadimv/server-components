package rsp.http.openapi;

import org.junit.jupiter.api.Test;
import rsp.http.HttpMethod;
import rsp.http.HttpStatus;
import rsp.http.routing.HttpRouteHandler;
import rsp.http.routing.HttpRouter;
import rsp.util.json.Json;
import rsp.util.json.JsonDataType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenApiDocumentTests {
    private static final HttpRouteHandler OK = HttpRouteHandler.sync((_, _) ->
            rsp.http.HttpResponse.ok().build());

    @Test
    void generates_openapi_31_from_documented_exact_routes() {
        OpenApiSchema message = OpenApiSchema.object()
                .requiredProperty("message", OpenApiSchema.string())
                .additionalProperties(false);
        OpenApiOperation hello = OpenApiOperation.builder()
                .operationId("hello")
                .summary("Return a greeting")
                .tag("messages")
                .jsonResponse(200, "Greeting", OpenApiSchema.ref("Message"))
                .build();
        OpenApiOperation create = OpenApiOperation.builder()
                .operationId("createMessage")
                .tag("messages")
                .jsonRequestBody(OpenApiSchema.ref("Message"), true)
                .jsonResponse(201, "Created message", OpenApiSchema.ref("Message"))
                .response(400, "Invalid request")
                .build();
        HttpRouter routes = HttpRouter.builder()
                .get("/api/hello/{name}", OK, hello)
                .post("/api/messages", OK, create)
                .get("/internal/health", OK)
                .build();

        OpenApiDocument document = OpenApiDocument.builder(
                        new OpenApiInfo("Example API", "1.0.0"), routes)
                .componentSchema("Message", message)
                .build();

        JsonDataType.Object root = document.json();
        assertEquals("3.1.0", root.requiredString("openapi"));
        JsonDataType.Object paths = root.requiredObject("paths");
        assertFalse(paths.keys().contains("/internal/health"));
        JsonDataType.Object get = paths.requiredObject("/api/hello/{name}").requiredObject("get");
        assertEquals("hello", get.requiredString("operationId"));
        JsonDataType.Object parameter = Json.requireObject(get.requiredArray("parameters").get(0));
        assertEquals("name", parameter.requiredString("name"));
        assertEquals("path", parameter.requiredString("in"));
        assertEquals(true, parameter.requiredBoolean("required"));
        assertEquals("string", root.requiredObject("components")
                .requiredObject("schemas").requiredObject("Message")
                .requiredObject("properties").requiredObject("message")
                .requiredString("type"));
        assertEquals("#/components/schemas/Message", get.requiredObject("responses")
                .requiredObject("200").requiredObject("content")
                .requiredObject("application/json").requiredObject("schema")
                .requiredString("$ref"));
    }

    @Test
    void document_handler_serves_deterministic_json() {
        HttpRouter routes = HttpRouter.builder()
                .get("/health", OK, OpenApiOperation.builder()
                        .operationId("health")
                        .response(204, "Healthy")
                        .build())
                .build();
        OpenApiDocument document = OpenApiDocument.generate(new OpenApiInfo("Health", "1"), routes);

        var response = document.handler().handle(null, null).toCompletableFuture().join();

        assertEquals(HttpStatus.OK, response.status());
        assertEquals("application/json; charset=utf-8", response.header("Content-Type"));
        assertEquals(document.text(), read(response));
    }

    @Test
    void rejects_metadata_that_cannot_describe_the_registered_routes() {
        OpenApiOperation wrongPath = OpenApiOperation.builder()
                .pathParameter("other", OpenApiSchema.string())
                .response(200, "OK")
                .build();
        HttpRouter wrongPathRouter = HttpRouter.builder().get("/items/{id}", OK, wrongPath).build();
        assertThrows(IllegalStateException.class, () -> OpenApiDocument.generate(
                new OpenApiInfo("API", "1"), wrongPathRouter));

        OpenApiOperation duplicate = OpenApiOperation.builder()
                .operationId("same")
                .response(200, "OK")
                .build();
        HttpRouter duplicateIds = HttpRouter.builder()
                .get("/one", OK, duplicate)
                .post("/two", OK, duplicate)
                .build();
        assertThrows(IllegalStateException.class, () -> OpenApiDocument.generate(
                new OpenApiInfo("API", "1"), duplicateIds));

        HttpRouter connect = HttpRouter.builder()
                .route(HttpMethod.CONNECT, "/tunnel", OK, duplicate)
                .build();
        assertThrows(IllegalStateException.class, () -> OpenApiDocument.generate(
                new OpenApiInfo("API", "1"), connect));
    }

    @Test
    void operation_requires_a_response_and_path_parameters_are_required() {
        assertThrows(IllegalStateException.class, () -> OpenApiOperation.builder().build());
        assertThrows(IllegalArgumentException.class, () -> OpenApiOperation.builder()
                .parameter("id", OpenApiOperation.ParameterLocation.PATH, false,
                        null, OpenApiSchema.string()));
    }

    private static String read(rsp.http.HttpResponse response) {
        try (var input = response.body().openStream()) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
