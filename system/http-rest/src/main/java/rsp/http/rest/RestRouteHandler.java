package rsp.http.rest;

import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.json.JsonHttp;
import rsp.http.json.JsonHttpException;
import rsp.http.routing.HttpRouteContext;
import rsp.http.routing.HttpRouteHandler;
import rsp.util.json.Json;
import rsp.util.json.JsonCodec;
import rsp.util.json.JsonDataType;
import rsp.util.json.JsonParser;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * HTTP route handler that maps expected REST and JSON failures to JSON responses.
 * Unexpected failures remain failed stages for the transport's {@code 500} boundary.
 */
@FunctionalInterface
public interface RestRouteHandler extends HttpRouteHandler {
    /** Protects an asynchronous HTTP handler with REST error mapping. */
    static RestRouteHandler async(HttpRouteHandler handler) {
        Objects.requireNonNull(handler, "handler");
        return (request, route) -> {
            try {
                return recover(Objects.requireNonNull(handler.handle(request, route),
                        "handler completion stage"));
            } catch (Throwable failure) {
                return recover(CompletableFuture.failedFuture(failure));
            }
        };
    }

    /** Adapts and protects a synchronous HTTP handler. */
    static RestRouteHandler sync(HttpRouteHandler.Synchronous handler) {
        return async(HttpRouteHandler.sync(Objects.requireNonNull(handler, "handler")));
    }

    /** Parses a JSON body and passes its immutable value tree to an asynchronous endpoint. */
    static RestRouteHandler json(JsonEndpoint<JsonDataType> endpoint) {
        return json(Json.parser(), JsonCodec.tree(), endpoint);
    }

    /** Parses a JSON body with explicit limits and passes its value tree to an endpoint. */
    static RestRouteHandler json(JsonParser parser, JsonEndpoint<JsonDataType> endpoint) {
        return json(parser, JsonCodec.tree(), endpoint);
    }

    /** Parses and decodes a JSON body before invoking an asynchronous endpoint. */
    static <T> RestRouteHandler json(JsonCodec<T> codec, JsonEndpoint<T> endpoint) {
        return json(Json.parser(), codec, endpoint);
    }

    /** Parses and decodes a JSON body using an explicit parser/limit profile. */
    static <T> RestRouteHandler json(JsonParser parser, JsonCodec<T> codec, JsonEndpoint<T> endpoint) {
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(endpoint, "endpoint");
        return async((request, route) -> endpoint.handle(request, route,
                JsonHttp.read(request, parser, codec)));
    }

    /** Parses a JSON body and passes its immutable value tree to a synchronous endpoint. */
    static RestRouteHandler jsonSync(SynchronousJsonEndpoint<JsonDataType> endpoint) {
        return jsonSync(Json.parser(), JsonCodec.tree(), endpoint);
    }

    /** Parses a JSON body with explicit limits and passes its value tree to a synchronous endpoint. */
    static RestRouteHandler jsonSync(JsonParser parser,
                                     SynchronousJsonEndpoint<JsonDataType> endpoint) {
        return jsonSync(parser, JsonCodec.tree(), endpoint);
    }

    /** Parses and decodes a JSON body before invoking a synchronous endpoint. */
    static <T> RestRouteHandler jsonSync(JsonCodec<T> codec, SynchronousJsonEndpoint<T> endpoint) {
        return jsonSync(Json.parser(), codec, endpoint);
    }

    /** Parses and decodes a JSON body using an explicit parser/limit profile. */
    static <T> RestRouteHandler jsonSync(JsonParser parser,
                                         JsonCodec<T> codec,
                                         SynchronousJsonEndpoint<T> endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        return json(parser, codec, (request, route, body) -> CompletableFuture.completedFuture(
                Objects.requireNonNull(endpoint.handle(request, route, body), "endpoint response")));
    }

    private static CompletionStage<HttpResponse> recover(CompletionStage<HttpResponse> stage) {
        CompletableFuture<HttpResponse> result = new CompletableFuture<>();
        stage.whenComplete((response, failure) -> {
            if (failure == null) {
                if (response == null) {
                    result.completeExceptionally(new NullPointerException("handler response"));
                } else {
                    result.complete(response);
                }
                return;
            }

            Throwable cause = unwrap(failure);
            if (cause instanceof JsonHttpException jsonFailure) {
                result.complete(JsonHttp.error(jsonFailure));
            } else if (cause instanceof RestException restFailure) {
                result.complete(JsonHttp.error(restFailure.status(), restFailure.error(),
                        restFailure.getMessage()));
            } else {
                result.completeExceptionally(cause);
            }
        });
        return result;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable result = failure;
        while ((result instanceof CompletionException || result instanceof ExecutionException)
                && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    /** Asynchronous endpoint receiving a parsed and optionally decoded JSON body. */
    @FunctionalInterface
    interface JsonEndpoint<T> {
        CompletionStage<HttpResponse> handle(HttpRequest request, HttpRouteContext route, T body);
    }

    /** Synchronous endpoint receiving a parsed and optionally decoded JSON body. */
    @FunctionalInterface
    interface SynchronousJsonEndpoint<T> {
        HttpResponse handle(HttpRequest request, HttpRouteContext route, T body);
    }
}
