package rsp.http.openapi;

import rsp.http.routing.HttpRouteMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable OpenAPI operation metadata attached to one exact router registration. */
public final class OpenApiOperation implements HttpRouteMetadata {
    private final Optional<String> operationId;
    private final Optional<String> summary;
    private final Optional<String> description;
    private final List<String> tags;
    private final List<Parameter> parameters;
    private final Optional<RequestBody> requestBody;
    private final Map<String, Response> responses;

    private OpenApiOperation(Builder builder) {
        operationId = Optional.ofNullable(builder.operationId);
        summary = Optional.ofNullable(builder.summary);
        description = Optional.ofNullable(builder.description);
        tags = List.copyOf(builder.tags);
        parameters = List.copyOf(builder.parameters);
        requestBody = Optional.ofNullable(builder.requestBody);
        if (builder.responses.isEmpty()) {
            throw new IllegalStateException("An OpenAPI operation requires at least one response");
        }
        responses = Collections.unmodifiableMap(new LinkedHashMap<>(builder.responses));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<String> operationId() {
        return operationId;
    }

    public Optional<String> summary() {
        return summary;
    }

    public Optional<String> description() {
        return description;
    }

    public List<String> tags() {
        return tags;
    }

    public List<Parameter> parameters() {
        return parameters;
    }

    public Optional<RequestBody> requestBody() {
        return requestBody;
    }

    public Map<String, Response> responses() {
        return responses;
    }

    public enum ParameterLocation {
        PATH("path"), QUERY("query"), HEADER("header"), COOKIE("cookie");

        private final String value;

        ParameterLocation(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public record Parameter(String name,
                            ParameterLocation location,
                            boolean required,
                            Optional<String> description,
                            OpenApiSchema schema) {
        public Parameter {
            name = OpenApiInfo.requireText(name, "parameter name");
            Objects.requireNonNull(location, "location");
            description = Objects.requireNonNull(description, "description");
            description = description.map(value -> OpenApiInfo.requireText(value, "parameter description"));
            Objects.requireNonNull(schema, "schema");
            if (location == ParameterLocation.PATH && !required) {
                throw new IllegalArgumentException("OpenAPI path parameters must be required");
            }
        }
    }

    public record RequestBody(boolean required,
                              Optional<String> description,
                              Map<String, OpenApiSchema> content) {
        public RequestBody {
            description = Objects.requireNonNull(description, "description");
            description = description.map(value -> OpenApiInfo.requireText(value, "request body description"));
            content = immutableContent(content);
            if (content.isEmpty()) {
                throw new IllegalArgumentException("OpenAPI request body content must not be empty");
            }
        }
    }

    public record Response(String description, Map<String, OpenApiSchema> content) {
        public Response {
            description = OpenApiInfo.requireText(description, "response description");
            content = immutableContent(content);
        }
    }

    public static final class Builder {
        private String operationId;
        private String summary;
        private String description;
        private final Set<String> tags = new LinkedHashSet<>();
        private final List<Parameter> parameters = new ArrayList<>();
        private RequestBody requestBody;
        private final Map<String, Response> responses = new LinkedHashMap<>();

        public Builder operationId(String value) {
            operationId = OpenApiInfo.requireText(value, "operationId");
            return this;
        }

        public Builder summary(String value) {
            summary = OpenApiInfo.requireText(value, "summary");
            return this;
        }

        public Builder description(String value) {
            description = OpenApiInfo.requireText(value, "description");
            return this;
        }

        public Builder tag(String value) {
            tags.add(OpenApiInfo.requireText(value, "tag"));
            return this;
        }

        public Builder pathParameter(String name, OpenApiSchema schema) {
            return parameter(name, ParameterLocation.PATH, true, null, schema);
        }

        public Builder queryParameter(String name, boolean required, OpenApiSchema schema) {
            return parameter(name, ParameterLocation.QUERY, required, null, schema);
        }

        public Builder headerParameter(String name, boolean required, OpenApiSchema schema) {
            return parameter(name, ParameterLocation.HEADER, required, null, schema);
        }

        public Builder cookieParameter(String name, boolean required, OpenApiSchema schema) {
            return parameter(name, ParameterLocation.COOKIE, required, null, schema);
        }

        public Builder parameter(String name,
                                 ParameterLocation location,
                                 boolean required,
                                 String description,
                                 OpenApiSchema schema) {
            Parameter parameter = new Parameter(name, location, required,
                    Optional.ofNullable(description), schema);
            boolean duplicate = parameters.stream().anyMatch(existing ->
                    existing.location() == location && existing.name().equals(name));
            if (duplicate) {
                throw new IllegalArgumentException("Duplicate OpenAPI parameter: " + location.value() + " " + name);
            }
            parameters.add(parameter);
            return this;
        }

        public Builder jsonRequestBody(OpenApiSchema schema, boolean required) {
            return requestBody("application/json", schema, required, null);
        }

        public Builder requestBody(String mediaType,
                                   OpenApiSchema schema,
                                   boolean required,
                                   String description) {
            return requestBody(Map.of(requireMediaType(mediaType),
                    Objects.requireNonNull(schema, "schema")), required, description);
        }

        public Builder requestBody(Map<String, OpenApiSchema> content,
                                   boolean required,
                                   String description) {
            if (requestBody != null) {
                throw new IllegalStateException("OpenAPI request body is already defined");
            }
            requestBody = new RequestBody(required, Optional.ofNullable(description), content);
            return this;
        }

        public Builder response(int status, String description) {
            return response(Integer.toString(requireStatus(status)), description, Map.of());
        }

        public Builder jsonResponse(int status, String description, OpenApiSchema schema) {
            return response(Integer.toString(requireStatus(status)), description,
                    Map.of("application/json", Objects.requireNonNull(schema, "schema")));
        }

        public Builder response(int status,
                                String description,
                                String mediaType,
                                OpenApiSchema schema) {
            return response(Integer.toString(requireStatus(status)), description,
                    Map.of(requireMediaType(mediaType), Objects.requireNonNull(schema, "schema")));
        }

        public Builder response(int status,
                                String description,
                                Map<String, OpenApiSchema> content) {
            return response(Integer.toString(requireStatus(status)), description, content);
        }

        public Builder defaultResponse(String description) {
            return response("default", description, Map.of());
        }

        public Builder defaultResponse(String description, Map<String, OpenApiSchema> content) {
            return response("default", description, content);
        }

        public OpenApiOperation build() {
            return new OpenApiOperation(this);
        }

        private Builder response(String status, String description, Map<String, OpenApiSchema> content) {
            Response previous = responses.putIfAbsent(status, new Response(description, content));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate OpenAPI response status: " + status);
            }
            return this;
        }

        private static int requireStatus(int status) {
            if (status < 100 || status > 599) {
                throw new IllegalArgumentException("OpenAPI response status must be between 100 and 599");
            }
            return status;
        }
    }

    private static Map<String, OpenApiSchema> immutableContent(Map<String, OpenApiSchema> content) {
        Objects.requireNonNull(content, "content");
        LinkedHashMap<String, OpenApiSchema> result = new LinkedHashMap<>();
        content.forEach((mediaType, schema) -> result.put(requireMediaType(mediaType),
                Objects.requireNonNull(schema, "schema")));
        return Collections.unmodifiableMap(result);
    }

    private static String requireMediaType(String value) {
        String mediaType = OpenApiInfo.requireText(value, "mediaType");
        if (!mediaType.contains("/")) {
            throw new IllegalArgumentException("Invalid media type: " + mediaType);
        }
        return mediaType;
    }
}
