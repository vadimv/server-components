package rsp.actor.http;

import rsp.actor.ActorAskTimeoutException;
import rsp.actor.ActorDeliveryException;
import rsp.actor.ActorEnvelope;
import rsp.actor.ActorRef;
import rsp.actor.ActorSystem;
import rsp.actor.SendResult;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.json.JsonHttp;
import rsp.http.rest.RestException;
import rsp.http.rest.RestRouteHandler;
import rsp.http.routing.HttpRouteContext;
import rsp.util.json.JsonCodec;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Adapts ordinary HTTP routes to local actor ask/reply without owning routing or lifecycle. */
public final class ActorRouteHandler {
    private ActorRouteHandler() {
    }

    /** Sends a command and maps its reply to an HTTP response. */
    public static <M, R> RestRouteHandler ask(
            ActorSystem actors,
            BiFunction<HttpRequest, HttpRouteContext, ActorRef<M>> target,
            Command<M, R> command,
            Duration timeout,
            Function<R, HttpResponse> response) {
        Objects.requireNonNull(command, "command");
        return askEnvelope(actors, target,
                (request, route, replyTo) -> ActorEnvelope.of(command.create(request, route, replyTo)),
                timeout, response);
    }

    /** Like {@link #ask}, with caller-supplied message identity and correlation metadata. */
    public static <M, R> RestRouteHandler askEnvelope(
            ActorSystem actors,
            BiFunction<HttpRequest, HttpRouteContext, ActorRef<M>> target,
            EnvelopeCommand<M, R> command,
            Duration timeout,
            Function<R, HttpResponse> response) {
        Objects.requireNonNull(actors, "actors");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(command, "command");
        requireTimeout(timeout);
        Objects.requireNonNull(response, "response");
        return RestRouteHandler.async((request, route) -> dispatch(actors,
                Objects.requireNonNull(target.apply(request, route), "actor target"),
                replyTo -> command.create(request, route, replyTo), timeout, response));
    }

    /** Decodes a JSON request and encodes the actor reply as a 200 JSON response. */
    public static <B, M, R> RestRouteHandler json(
            ActorSystem actors,
            JsonCodec<B> requestCodec,
            BiFunction<HttpRequest, HttpRouteContext, ActorRef<M>> target,
            JsonCommand<B, M, R> command,
            Duration timeout,
            JsonCodec<R> responseCodec) {
        Objects.requireNonNull(command, "command");
        return jsonEnvelope(actors, requestCodec, target,
                (request, route, body, replyTo) ->
                        ActorEnvelope.of(command.create(request, route, body, replyTo)),
                timeout, responseCodec);
    }

    /** JSON ask with caller-supplied delivery metadata, e.g. from an Idempotency-Key header. */
    public static <B, M, R> RestRouteHandler jsonEnvelope(
            ActorSystem actors,
            JsonCodec<B> requestCodec,
            BiFunction<HttpRequest, HttpRouteContext, ActorRef<M>> target,
            JsonEnvelopeCommand<B, M, R> command,
            Duration timeout,
            JsonCodec<R> responseCodec) {
        Objects.requireNonNull(actors, "actors");
        Objects.requireNonNull(requestCodec, "requestCodec");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(command, "command");
        requireTimeout(timeout);
        Objects.requireNonNull(responseCodec, "responseCodec");
        return RestRouteHandler.json(requestCodec, (request, route, body) -> ActorRouteHandler.<M, R>dispatch(actors,
                Objects.requireNonNull(target.apply(request, route), "actor target"),
                replyTo -> command.create(request, route, body, replyTo), timeout,
                reply -> JsonHttp.response(reply, responseCodec)));
    }

    private static <M, R> CompletionStage<HttpResponse> dispatch(
            ActorSystem actors, ActorRef<M> target,
            Function<ActorRef<R>, ActorEnvelope<M>> command,
            Duration timeout, Function<R, HttpResponse> response) {
        return actors.askEnvelope(target, command, timeout).handle((reply, failure) -> {
            if (failure != null) {
                throw mapFailure(failure);
            }
            return Objects.requireNonNull(response.apply(reply), "actor route response");
        });
    }

    private static RuntimeException mapFailure(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ActorDeliveryException rejected) {
            if (rejected.result() == SendResult.MAILBOX_FULL
                    || rejected.result() == SendResult.STOPPED
                    || rejected.result() == SendResult.NOT_STARTED) {
                return new RestException(HttpStatus.SERVICE_UNAVAILABLE, "actor_unavailable",
                        "The actor is temporarily unavailable", rejected);
            }
        }
        if (cause instanceof ActorAskTimeoutException timeout) {
            return new RestException(HttpStatus.GATEWAY_TIMEOUT, "actor_timeout",
                    "The actor did not reply before the deadline", timeout);
        }
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        return new CompletionException(cause);
    }

    private static void requireTimeout(Duration timeout) {
        if (Objects.requireNonNull(timeout, "timeout").isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    @FunctionalInterface
    public interface Command<M, R> {
        M create(HttpRequest request, HttpRouteContext route, ActorRef<R> replyTo);
    }

    @FunctionalInterface
    public interface EnvelopeCommand<M, R> {
        ActorEnvelope<M> create(HttpRequest request, HttpRouteContext route, ActorRef<R> replyTo);
    }

    @FunctionalInterface
    public interface JsonCommand<B, M, R> {
        M create(HttpRequest request, HttpRouteContext route, B body, ActorRef<R> replyTo);
    }

    @FunctionalInterface
    public interface JsonEnvelopeCommand<B, M, R> {
        ActorEnvelope<M> create(HttpRequest request, HttpRouteContext route, B body,
                                ActorRef<R> replyTo);
    }
}
