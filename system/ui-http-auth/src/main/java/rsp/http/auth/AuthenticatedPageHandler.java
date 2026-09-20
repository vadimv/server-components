package rsp.http.auth;

import rsp.authentication.Authentication;
import rsp.http.HttpRequest;
import rsp.http.HttpResult;

/** Creates a page or HTTP response after an authentication adapter establishes identity. */
@FunctionalInterface
public interface AuthenticatedPageHandler {
    HttpResult handle(HttpRequest request, Authentication authentication);
}
