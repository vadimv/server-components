package rsp.html;

import rsp.component.ComponentRenderContext;
import rsp.dom.XmlNs;
import rsp.page.PageRenderContext;

import java.util.*;

import static rsp.server.http.HttpResponse.MOVED_TEMPORARILY_STATUS_CODE;

/**
 * A definition of an HTML document.
 */
public final class HtmlDocument extends Tag {

    private final int statusCode;
    private final Map<String, List<String>> headers;

    public HtmlDocument(final int statusCode, final Map<String, List<String>> headers, final SegmentDefinition... children) {
        super(XmlNs.html, "html", children);
        this.statusCode = statusCode;
        this.headers = Objects.requireNonNull(headers);
    }

    @Override
    public boolean render(final ComponentRenderContext renderContext) {
        if (renderContext instanceof PageRenderContext pageRenderContext) {
            pageRenderContext.setStatusCode(statusCode);
            pageRenderContext.setHeaders(headers);
        }

        renderContext.setDocType("<!DOCTYPE html>");
        super.render(renderContext);
        return true;
    }

    /**
     * Sets the HTTP status code to be rendered in the response.
     * @param statusCode status code
     * @return an instance with the status code
     */
    public HtmlDocument statusCode(final int statusCode) {
        return new HtmlDocument(statusCode, this.headers, this.children);
    }

    /**
     * Adds the HTTP headers to be rendered in the response.
     * @param name a header's name
     * @param value a headers' value
     * @return an instance with added headers
     */

    public HtmlDocument addHeader(String name, String value) {
        return new HtmlDocument(this.statusCode, addHeader(this.headers, name, value), this.children);
    }

    /**
     * Sets redirect status code and the Location header.
     * @param location Location header for redirection
     * @return and instance with the redirection status code and header
     */
    public HtmlDocument redirect(final String location) {
        return new HtmlDocument(MOVED_TEMPORARILY_STATUS_CODE,
                                          addHeader(this.headers, "Location", location), this.children);
    }

    private static Map<String, List<String>> addHeader(Map<String, List<String>> headers, String name, String value) {
        final Map<String, List<String>> result = new HashMap<>(headers);
        final List<String> values = headers.get(name);
        if (values == null) {
            result.put(name, List.of(value));
        } else {
            final List<String> tmpList = new ArrayList<>(values);
            tmpList.add(value);
            result.put(name, Collections.unmodifiableList(tmpList));
        }

        return result;
    }
}
