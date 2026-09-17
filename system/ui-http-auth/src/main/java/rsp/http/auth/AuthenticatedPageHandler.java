package rsp.http.auth;

import rsp.authentication.Authentication;
import rsp.http.HttpRequest;
import rsp.http.PageResult;

/** Creates a page result after an HTTP authentication adapter establishes identity. */
@FunctionalInterface
public interface AuthenticatedPageHandler {
    PageResult handle(HttpRequest request, Authentication authentication);
}
