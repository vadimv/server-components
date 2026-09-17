package rsp.http.auth;

import rsp.authentication.Authentication;
import rsp.http.HttpRequest;

/** Establishes an immutable identity from one HTTP request. */
@FunctionalInterface
public interface HttpAuthenticator {
    Authentication authenticate(HttpRequest request);
}
