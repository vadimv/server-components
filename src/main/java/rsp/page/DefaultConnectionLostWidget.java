package rsp.page;

import rsp.dom.DomTreeRenderContext;
import rsp.dom.VirtualDomPath;
import rsp.html.TagDefinition;

import static rsp.html.HtmlDsl.*;

public final class DefaultConnectionLostWidget {

    private DefaultConnectionLostWidget() {}

    public static final String HTML;

    static {
        final RenderContext rc = new DomTreeRenderContext(VirtualDomPath.DOCUMENT);
        widget().render(rc);
        HTML = rc.toString();
    }

    private static TagDefinition widget() {
        return div(style("position", "fixed"),
                   style("top", "0"),
                   style("left", "0"),
                   style("right", "0"),
                   style("background-color", "lightyellow"),
                   style("border-bottom", "1px solid black"),
                   style("padding", "10px"),
                   text("Connection lost. Waiting to resume."));
    }
}
