package rsp.http.auth;

import rsp.authentication.Authentication;
import rsp.http.HttpRequest;
import rsp.http.routing.HttpRouter;

/** Establishes an immutable identity from one HTTP request. */
@FunctionalInterface
public interface HttpAuthenticator {
    Authentication authenticate(HttpRequest request);

    /** HTTP endpoints owned by this authentication mechanism, if any. */
    default HttpRouter routes() {
        return HttpRouter.builder().build();
    }
}
