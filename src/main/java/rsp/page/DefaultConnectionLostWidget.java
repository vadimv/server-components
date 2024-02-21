package rsp.page;

import rsp.component.ComponentRenderContext;
import rsp.dom.VirtualDomPath;
import rsp.html.TagDefinition;
import rsp.html.HtmlDsl;
import rsp.server.Path;
import rsp.server.http.HttpRequest;
import rsp.server.http.HttpStateOrigin;
import rsp.server.http.RelativeUrl;
import rsp.server.http.HttpStateOriginLookup;

import java.net.URI;

public final class DefaultConnectionLostWidget {

    private DefaultConnectionLostWidget() {}

    public static final String HTML;

    static {
        final HttpRequest httpRequest = new HttpRequest(HttpRequest.HttpMethod.GET,
                                                        URI.create("about:widget"),
                                                        "about:widget",
                                                        Path.EMPTY_ABSOLUTE);
        final RenderContext rc = new ComponentRenderContext(VirtualDomPath.DOCUMENT,
                                                            Path.of(""),
                                                            new HttpStateOriginLookup(new HttpStateOrigin(httpRequest,
                                                                                                          RelativeUrl.of(httpRequest))),
                                                            new TemporaryBufferedPageCommands());
        widget().render(rc);
        HTML = rc.toString();
    }

    private static TagDefinition widget() {
        return HtmlDsl.div( HtmlDsl.style("position", "fixed"),
                            HtmlDsl.style("top", "0"),
                            HtmlDsl.style("left", "0"),
                            HtmlDsl.style("right", "0"),
                            HtmlDsl.style("background-color", "lightyellow"),
                            HtmlDsl.style("border-bottom", "1px solid black"),
                            HtmlDsl.style("padding", "10px"),
                            HtmlDsl.text("Connection lost. Waiting to resume."));
    }
}
