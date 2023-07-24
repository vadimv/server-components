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

    // TODO refactor to remove
    public final static HttpRequest DUMMY = new HttpRequest(HttpMethod.GET, URI.create("about.blank"), "", Path.EMPTY_ABSOLUTE);

    public final HttpMethod method;
    public final URI uri;
    public final String url;
    public final Path path;
    public final Function<String, Optional<String>> getQueryParam;
    public final Function<String, Optional<String>> getHeader;

    /**
     * Creates a new instance of an HTTP request.
     * @param method the HTTP verb
     * @param uri the request's URI
     * @param url the request's URL
     * @param path the request's path
     * @param queryParam the function that provides access the request's query parameters
     * @param getHeader the function that provides access to the request's headers
     */
    public HttpRequest(final HttpMethod method,
                       final URI uri,
                       final String url,
                       final Path path,
                       final Function<String, Optional<String>> queryParam,
                       final Function<String, Optional<String>> getHeader) {
        this.method = Objects.requireNonNull(method);
        this.uri = Objects.requireNonNull(uri);
        this.url = Objects.requireNonNull(url);
        this.path = Objects.requireNonNull(path);
        this.getQueryParam = Objects.requireNonNull(queryParam);
        this.getHeader = Objects.requireNonNull(getHeader);
    }

    /**
     * Creates a new instance of a HTTP request.
     * @param method the HTTP verb
     * @param uri the request's URI
     * @param url the request's URL
     * @param path the request's path
     */
    public HttpRequest(final HttpMethod method,
                       final URI uri,
                       final String url,
                       final Path path) {
        this.method = method;
        this.uri = uri;
        this.url = url;
        this.path = path;
        this.getQueryParam = n -> Optional.empty();
        this.getHeader = n -> Optional.empty();
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
     * Gets an unique ID of the browser.
     * @return the Optional with the device ID value or the empty
     */
    public Optional<String> deviceId() {
        return cookie(PageRendering.DEVICE_ID_COOKIE_NAME);
    }

    /**
     * Gets the request's query parameter by name.
     * @param name the parameter's name
     * @return the Optional with the parameter's value or the empty
     */
    public Optional<String> queryParam(final String name) {
        return getQueryParam.apply(name);
    }

    /**
     * Get the request's header by name.
     * @param headerName the header's name
     * @return an optional with the header's value or empty
     */
    public Optional<String> header(final String headerName) {
        return getHeader.apply(headerName);
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
