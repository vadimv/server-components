package rsp.server.http;

import rsp.page.HttpHandler;
import rsp.server.Path;

import java.net.URI;
import java.util.*;

/**
 * Represents an HTTP request.
 */
public final class HttpRequest {

    public final HttpMethod method;
    public final URI uri;
    public final String url;
    public final Path path;

    public final Query queryParameters;
    private final List<Header> headers;

    /**
     * Creates a new instance of an HTTP request.
     * @param method the HTTP verb
     * @param uri the request's URI
     * @param url the request's URL
     * @param path the request's componentPath
     * @param queryParameters the function that provides access the request's query parameters
     * @param headers the request's headers
     */
    public HttpRequest(final HttpMethod method,
                       final URI uri,
                       final String url,
                       final Path path,
                       final Query queryParameters,
                       final List<Header> headers) {
        this.method = Objects.requireNonNull(method);
        this.uri = Objects.requireNonNull(uri);
        this.url = Objects.requireNonNull(url);
        this.path = Objects.requireNonNull(path);
        this.queryParameters = Objects.requireNonNull(queryParameters);
        this.headers = Objects.requireNonNull(headers);
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
        this(method, uri, url, path, Query.EMPTY, List.of());
    }

    /**
     * Gets a request's HTTP cookies by name.
     * @param cookieName the cookie name, must not be null
     * @return the Optional with the cookie value or the empty
     */
    public List<String> cookies(final String cookieName) {
        Objects.requireNonNull(cookieName);
        return headers.stream().filter(header -> "Cookie".equals(header.name())).flatMap(header ->
                Arrays.stream(header.value().split(";"))
                      .map(String::trim)
                      .map(pairStr -> Arrays.stream(pairStr.split("=")).toArray(String[]::new))
                      .filter(pair -> pair.length == 2 && pair[0].equals(cookieName))
                      .map(pair -> pair[1])).toList();
    }

    /**
     * Gets a unique ID of the browser.
     * @return the Optional with the device ID value or the empty
     */
    public Optional<String> deviceId() {
        return cookies(HttpHandler.DEVICE_ID_COOKIE_NAME).stream().findFirst();
    }


    /**
     * Gets the request's header by name.
     * @param headerName the header's name, must not be null
     * @return an optional with the header's value or empty
     */
    public Optional<String> header(final String headerName) {
        Objects.requireNonNull(headerName);
        return headers.stream().filter(h -> headerName.equals(h.name())).map(Header::value).findFirst();
    }

    /**
     * Gets a relative URL.
     * @return a relative URL with an empty fragment
     */
    public RelativeUrl relativeUrl() {
        return new RelativeUrl(this.path, this.queryParameters, Fragment.EMPTY);
    }
}
