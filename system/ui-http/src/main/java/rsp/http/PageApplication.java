package rsp.http;

import rsp.application.ApplicationLifecycle;

import java.util.Objects;

/** Selects the initial UI outcome for an HTTP request. */
@FunctionalInterface
public interface PageApplication extends ApplicationLifecycle {
    PageResult handle(HttpRequest request);

    /** Associates process lifecycle with a page-selection function. */
    static PageApplication withLifecycle(ApplicationLifecycle lifecycle, PageApplication application) {
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(application, "application");
        return new PageApplication() {
            @Override
            public PageResult handle(HttpRequest request) {
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
