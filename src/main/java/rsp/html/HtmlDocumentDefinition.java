package rsp.html;

import rsp.dom.XmlNs;
import rsp.page.RenderContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static rsp.server.http.HttpResponse.MOVED_TEMPORARILY_STATUS_CODE;

/**
 * A definition of an HTML document.
 */
public final class HtmlDocumentDefinition extends TagDefinition {

    private final int statusCode;
    private final Map<String, String> headers;

    public HtmlDocumentDefinition(final int statusCode, final Map<String, String> headers, final SegmentDefinition... children) {
        super(XmlNs.html, "html", children);
        this.statusCode = statusCode;
        this.headers = Objects.requireNonNull(headers);
    }

    @Override
    public boolean render(final RenderContext renderContext) {
        renderContext.setStatusCode(statusCode);
        renderContext.setHeaders(headers);
        renderContext.setDocType("<!DOCTYPE html>");
        super.render(renderContext);
        return true;
    }

    /**
     * Sets the HTTP status code to be rendered in the response.
     * @param statusCode status code
     * @return an instance with the status code
     */
    public HtmlDocumentDefinition statusCode(final int statusCode) {
        return new HtmlDocumentDefinition(statusCode, this.headers, this.children);
    }

    /**
     * Adds the HTTP headers to be rendered in the response.
     * @param headers the map containing headers
     * @return an instance with added headers
     */
    public HtmlDocumentDefinition addHeaders(final Map<String, String> headers) {
        return new HtmlDocumentDefinition(this.statusCode, mergeMaps(this.headers, headers), this.children);
    }

    /**
     * Sets redirect status code and the Location header.
     * @param location Location header for redirection
     * @return and instance with the redirection status code and header
     */
    public HtmlDocumentDefinition redirect(final String location) {
        return new HtmlDocumentDefinition(MOVED_TEMPORARILY_STATUS_CODE,
                                          mergeMaps(this.headers, Map.of("Location", location)), this.children);
    }

    private static Map<String, String> mergeMaps(final Map<String, String> m1, final Map<String, String> m2) {
        final Map<String, String> result = new HashMap<>(m1);
        result.putAll(m2);
        return result;
    }
}
