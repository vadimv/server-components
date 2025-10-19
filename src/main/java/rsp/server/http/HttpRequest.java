package rsp.server.http;

import rsp.page.PageRendering;
import rsp.server.Path;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Represents an HTTP request.
 */
public final class HttpRequest {

    public final HttpMethod method;
    public final URI uri;
    public final String url;
    public final Path path;

    public final Query queryParameters;
    private final Function<String, Optional<String>> getHeader;

    /**
     * Creates a new instance of an HTTP request.
     * @param method the HTTP verb
     * @param uri the request's URI
     * @param url the request's URL
     * @param path the request's componentPath
     * @param queryParameters the function that provides access the request's query parameters
     * @param getHeader the function that provides access to the request's headers
     */
    public HttpRequest(final HttpMethod method,
                       final URI uri,
                       final String url,
                       final Path path,
                       final Query queryParameters,
                       final Function<String, Optional<String>> getHeader) {
        this.method = Objects.requireNonNull(method);
        this.uri = Objects.requireNonNull(uri);
        this.url = Objects.requireNonNull(url);
        this.path = Objects.requireNonNull(path);
        this.queryParameters = Objects.requireNonNull(queryParameters);
        this.getHeader = Objects.requireNonNull(getHeader);
    }

    /**
     * Creates a new instance of an HTTP request.
     * @param method the HTTP verb
     * @param uri the request's URI
     * @param url the request's URL
     * @param path the request's componentPath
     */
    public HttpRequest(final HttpMethod method,
                       final URI uri,
                       final String url,
                       final Path path) {
        this(method, uri, url, path, Query.EMPTY, __ -> Optional.empty());
    }

    /**
     * Gets a request's HTTP cookie by name.
     * @param cookieName the cookie name
     * @return the Optional with the cookie value or the empty
     */
    public Optional<String> cookie(final String cookieName) {
        return getHeader.apply("Cookie").flatMap(headerValue ->
                Arrays.stream(headerValue.split(";"))
                      .map(String::trim)
                      .map(pairStr -> Arrays.stream(pairStr.split("=")).toArray(String[]::new))
                      .filter(pair -> pair.length == 2 && pair[0].equals(cookieName)).findFirst()
                      .map(pair -> pair[1]));
    }

    /**
     * Gets a unique ID of the browser.
     * @return the Optional with the device ID value or the empty
     */
    public Optional<String> deviceId() {
        return cookie(PageRendering.DEVICE_ID_COOKIE_NAME);
    }


    /**
     * Get the request's header by name.
     * @param headerName the header's name
     * @return an optional with the header's value or empty
     */
    public Optional<String> header(final String headerName) {
        return getHeader.apply(headerName);
    }


    public RelativeUrl relativeUrl() {
        return new RelativeUrl(this.path, this.queryParameters, Fragment.EMPTY);
    }

    /**
     * HTTP verbs.
     */
    public enum HttpMethod {
        GET,
        HEAD,
        POST,
        PUT,
        DELETE,
        CONNECT,
        OPTIONS,
        TRACE,
        PATCH
    }
}
