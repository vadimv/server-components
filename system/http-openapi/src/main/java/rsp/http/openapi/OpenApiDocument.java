package rsp.http.openapi;

import rsp.http.HttpMethod;
import rsp.http.HttpResponse;
import rsp.http.json.JsonHttp;
import rsp.http.routing.HttpRouteDefinition;
import rsp.http.routing.HttpRouteHandler;
import rsp.http.routing.HttpRouter;
import rsp.util.json.Json;
import rsp.util.json.JsonDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable OpenAPI 3.1 document generated from an {@link HttpRouter} snapshot. */
public final class OpenApiDocument {
    public static final String VERSION = "3.1.0";

    private final JsonDataType.Object document;

    private OpenApiDocument(JsonDataType.Object document) {
        this.document = document;
    }

    public static Builder builder(OpenApiInfo info, HttpRouter router) {
        return new Builder(info, router);
    }

    public static OpenApiDocument generate(OpenApiInfo info, HttpRouter router) {
        return builder(info, router).build();
    }

    public JsonDataType.Object json() {
        return document;
    }

    public String text() {
        return Json.write(document);
    }

    public HttpResponse response() {
        return JsonHttp.response(document);
    }

    /** A route handler serving this immutable document snapshot. */
    public HttpRouteHandler handler() {
        return HttpRouteHandler.sync((_, _) -> response());
    }

    public static final class Builder {
        private final OpenApiInfo info;
        private final HttpRouter router;
        private final Map<String, OpenApiSchema> componentSchemas = new LinkedHashMap<>();

        private Builder(OpenApiInfo info, HttpRouter router) {
            this.info = Objects.requireNonNull(info, "info");
            this.router = Objects.requireNonNull(router, "router");
        }

        public Builder componentSchema(String name, OpenApiSchema schema) {
            String componentName = OpenApiInfo.requireText(name, "component schema name");
            if (!componentName.matches("[A-Za-z0-9._-]+")) {
                throw new IllegalArgumentException("Invalid OpenAPI component name: " + componentName);
            }
            if (componentSchemas.putIfAbsent(componentName,
                    Objects.requireNonNull(schema, "schema")) != null) {
                throw new IllegalArgumentException("Duplicate OpenAPI component schema: " + componentName);
            }
            return this;
        }

        public OpenApiDocument build() {
            JsonDataType.Object root = Json.object()
                    .put("openapi", VERSION)
                    .put("info", infoJson())
                    .put("paths", pathsJson());
            if (!componentSchemas.isEmpty()) {
                JsonDataType.Object schemas = Json.object();
                for (Map.Entry<String, OpenApiSchema> entry : componentSchemas.entrySet()) {
                    schemas = schemas.put(entry.getKey(), entry.getValue().json());
                }
                root = root.put("components", Json.object().put("schemas", schemas));
            }
            return new OpenApiDocument(root);
        }

        private JsonDataType.Object infoJson() {
            JsonDataType.Object result = Json.object()
                    .put("title", info.title())
                    .put("version", info.version());
            if (info.description().isPresent()) {
                result = result.put("description", info.description().orElseThrow());
            }
            return result;
        }

        private JsonDataType.Object pathsJson() {
            List<DocumentedRoute> documented = router.routeDefinitions().stream()
                    .map(route -> route.metadata(OpenApiOperation.class)
                            .map(operation -> new DocumentedRoute(route, operation)))
                    .flatMap(java.util.Optional::stream)
                    .sorted(Comparator.comparing((DocumentedRoute route) ->
                                    route.route().template().toString())
                            .thenComparing(route -> route.route().method().ordinal()))
                    .toList();
            validateOperationIds(documented);

            Map<String, JsonDataType.Object> paths = new LinkedHashMap<>();
            for (DocumentedRoute route : documented) {
                requireSupportedMethod(route.route().method());
                String path = route.route().template().toString();
                JsonDataType.Object item = paths.getOrDefault(path, Json.object());
                String method = route.route().method().name().toLowerCase(Locale.ROOT);
                paths.put(path, item.put(method, operationJson(route)));
            }
            JsonDataType.Object result = Json.object();
            for (Map.Entry<String, JsonDataType.Object> path : paths.entrySet()) {
                result = result.put(path.getKey(), path.getValue());
            }
            return result;
        }

        private JsonDataType.Object operationJson(DocumentedRoute documented) {
            OpenApiOperation operation = documented.operation();
            JsonDataType.Object result = Json.object();
            if (operation.operationId().isPresent()) {
                result = result.put("operationId", operation.operationId().orElseThrow());
            }
            if (operation.summary().isPresent()) {
                result = result.put("summary", operation.summary().orElseThrow());
            }
            if (operation.description().isPresent()) {
                result = result.put("description", operation.description().orElseThrow());
            }
            if (!operation.tags().isEmpty()) {
                result = result.put("tags", strings(operation.tags()));
            }

            List<OpenApiOperation.Parameter> parameters = parameters(documented);
            if (!parameters.isEmpty()) {
                result = result.put("parameters", new JsonDataType.Array(parameters.stream()
                        .map(Builder::parameterJson).toArray(JsonDataType[]::new)));
            }
            if (operation.requestBody().isPresent()) {
                result = result.put("requestBody", requestBodyJson(operation.requestBody().orElseThrow()));
            }
            JsonDataType.Object responses = Json.object();
            for (Map.Entry<String, OpenApiOperation.Response> response : operation.responses().entrySet()) {
                responses = responses.put(response.getKey(), responseJson(response.getValue()));
            }
            return result.put("responses", responses);
        }

        private List<OpenApiOperation.Parameter> parameters(DocumentedRoute documented) {
            Set<String> templateParameters = new LinkedHashSet<>(
                    documented.route().template().parameterNames());
            List<OpenApiOperation.Parameter> result = new ArrayList<>(documented.operation().parameters());
            for (OpenApiOperation.Parameter parameter : result) {
                if (parameter.location() == OpenApiOperation.ParameterLocation.PATH
                        && !templateParameters.contains(parameter.name())) {
                    throw new IllegalStateException("OpenAPI path parameter '" + parameter.name()
                            + "' is not present in route " + documented.route().template());
                }
            }
            Set<String> explicitPathParameters = result.stream()
                    .filter(parameter -> parameter.location() == OpenApiOperation.ParameterLocation.PATH)
                    .map(OpenApiOperation.Parameter::name)
                    .collect(java.util.stream.Collectors.toSet());
            for (String name : templateParameters) {
                if (!explicitPathParameters.contains(name)) {
                    result.add(new OpenApiOperation.Parameter(name,
                            OpenApiOperation.ParameterLocation.PATH, true,
                            java.util.Optional.empty(), OpenApiSchema.string()));
                }
            }
            return List.copyOf(result);
        }

        private static JsonDataType.Object parameterJson(OpenApiOperation.Parameter parameter) {
            JsonDataType.Object result = Json.object()
                    .put("name", parameter.name())
                    .put("in", parameter.location().value())
                    .put("required", parameter.required())
                    .put("schema", parameter.schema().json());
            if (parameter.description().isPresent()) {
                result = result.put("description", parameter.description().orElseThrow());
            }
            return result;
        }

        private static JsonDataType.Object requestBodyJson(OpenApiOperation.RequestBody requestBody) {
            JsonDataType.Object result = Json.object()
                    .put("required", requestBody.required())
                    .put("content", contentJson(requestBody.content()));
            if (requestBody.description().isPresent()) {
                result = result.put("description", requestBody.description().orElseThrow());
            }
            return result;
        }

        private static JsonDataType.Object responseJson(OpenApiOperation.Response response) {
            JsonDataType.Object result = Json.object().put("description", response.description());
            if (!response.content().isEmpty()) {
                result = result.put("content", contentJson(response.content()));
            }
            return result;
        }

        private static JsonDataType.Object contentJson(Map<String, OpenApiSchema> content) {
            JsonDataType.Object result = Json.object();
            for (Map.Entry<String, OpenApiSchema> entry : content.entrySet()) {
                result = result.put(entry.getKey(), Json.object().put("schema", entry.getValue().json()));
            }
            return result;
        }

        private static JsonDataType.Array strings(List<String> values) {
            return new JsonDataType.Array(values.stream().map(Json::string).toArray(JsonDataType[]::new));
        }

        private static void validateOperationIds(List<DocumentedRoute> routes) {
            Set<String> ids = new HashSet<>();
            for (DocumentedRoute route : routes) {
                route.operation().operationId().ifPresent(id -> {
                    if (!ids.add(id)) {
                        throw new IllegalStateException("Duplicate OpenAPI operationId: " + id);
                    }
                });
            }
        }

        private static void requireSupportedMethod(HttpMethod method) {
            if (method == HttpMethod.CONNECT) {
                throw new IllegalStateException("OpenAPI path items do not support CONNECT operations");
            }
        }
    }

    private record DocumentedRoute(HttpRouteDefinition route, OpenApiOperation operation) {
    }
}
