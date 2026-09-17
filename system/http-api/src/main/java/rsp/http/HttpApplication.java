package rsp.http;

import rsp.application.ApplicationLifecycle;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** A transport-independent asynchronous HTTP application. */
@FunctionalInterface
public interface HttpApplication extends ApplicationLifecycle {
    CompletionStage<HttpResponse> handle(HttpRequest request);

    /** Associates process lifecycle with an HTTP handler without changing request dispatch. */
    static HttpApplication withLifecycle(ApplicationLifecycle lifecycle, HttpApplication application) {
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(application, "application");
        return new HttpApplication() {
            @Override
            public CompletionStage<HttpResponse> handle(HttpRequest request) {
                return application.handle(request);
            }

            @Override
            public void start() {
                lifecycle.start();
                try {
                    application.start();
                } catch (RuntimeException | Error failure) {
                    try {
                        lifecycle.stop();
                    } catch (RuntimeException | Error cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                    throw failure;
                }
            }

            @Override
            public void stop() {
                Throwable failure = null;
                try {
                    application.stop();
                } catch (RuntimeException | Error applicationFailure) {
                    failure = applicationFailure;
                }
                try {
                    lifecycle.stop();
                } catch (RuntimeException | Error lifecycleFailure) {
                    if (failure == null) {
                        failure = lifecycleFailure;
                    } else {
                        failure.addSuppressed(lifecycleFailure);
                    }
                }
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (failure instanceof Error error) {
                    throw error;
                }
            }
        };
    }
}
