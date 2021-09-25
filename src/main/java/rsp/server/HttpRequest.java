package rsp.server;

import rsp.page.PageRendering;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

/**
 * Represents an HTTP request.
 */
public final class HttpRequest {
    public final HttpMethod method;
    public final URI uri;
    public final Path path;
    public final Function<String, Optional<String>> getQueryParam;
    public final Function<String, Optional<String>> getHeader;

    /**
     * Creates a new instance of a HTTP request.
     * @param method the HTTP verb
     * @param uri the request's URI
     * @param path the request's path
     * @param queryParam the function that provides access the request's query parameters
     * @param getHeader the function that provides access to the request's headers
     */
    public HttpRequest(HttpMethod method,
                       URI uri,
                       Path path,
                       Function<String, Optional<String>> queryParam,
                       Function<String, Optional<String>> getHeader) {
        this.method = method;
        this.uri = uri;
        this.path = path;
        this.getQueryParam = queryParam;
        this.getHeader = getHeader;
    }

    /**
     * Gets a request's HTTP cookie by name.
     * @param cookieName the cookie name
     * @return the Optional with the cookie value or the empty
     */
    public Optional<String> cookie(String cookieName) {
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
    public Optional<String> queryParam(String name) {
        return getQueryParam.apply(name);
    }

    /**
     * Get the request's header by name.
     * @param headerName the header's name
     * @return an optional with the header's value or empty
     */
    public Optional<String> header(String headerName) {
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
