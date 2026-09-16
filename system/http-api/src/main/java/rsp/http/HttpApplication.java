package rsp.http;

import java.util.concurrent.CompletionStage;

/** A transport-independent asynchronous HTTP application. */
@FunctionalInterface
public interface HttpApplication {
    CompletionStage<HttpResponse> handle(HttpRequest request);
}
