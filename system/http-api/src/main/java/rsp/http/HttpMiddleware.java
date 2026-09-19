package rsp.http;

import rsp.application.ApplicationLifecycle;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Cross-cutting HTTP behavior applied around an {@link HttpApplication}.
 *
 * <p>Middleware is entered in registration order and observes responses in reverse order.
 * Its lifecycle surrounds the wrapped application's lifecycle.</p>
 */
@FunctionalInterface
public interface HttpMiddleware extends ApplicationLifecycle {
    CompletionStage<HttpResponse> handle(HttpRequest request, HttpApplication next);

    /** Wraps one application and preserves both lifecycles. */
    default HttpApplication wrap(HttpApplication application) {
        Objects.requireNonNull(application, "application");
        HttpApplication dispatch = request -> {
            try {
                return Objects.requireNonNull(handle(request, application), "middleware completion stage");
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        };
        return HttpApplication.withLifecycle(this,
                HttpApplication.withLifecycle(application, dispatch));
    }

    /**
     * Applies middleware in declaration order. The first item is the outermost request boundary.
     */
    static HttpApplication pipeline(HttpApplication application,
                                    HttpMiddleware... middleware) {
        Objects.requireNonNull(middleware, "middleware");
        return pipeline(application, List.of(middleware));
    }

    /**
     * Applies middleware in iteration order. The first item is the outermost request boundary.
     */
    static HttpApplication pipeline(HttpApplication application,
                                    List<? extends HttpMiddleware> middleware) {
        HttpApplication result = Objects.requireNonNull(application, "application");
        List<? extends HttpMiddleware> copy = List.copyOf(
                Objects.requireNonNull(middleware, "middleware"));
        for (int i = copy.size() - 1; i >= 0; i--) {
            result = copy.get(i).wrap(result);
        }
        return result;
    }
}
