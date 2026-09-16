package rsp.http;

/** Selects the initial UI outcome for an HTTP request. */
@FunctionalInterface
public interface PageApplication {
    PageResult handle(HttpRequest request);
}
