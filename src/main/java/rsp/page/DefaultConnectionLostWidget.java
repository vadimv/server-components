package rsp.page;

import rsp.dom.DomTreePageRenderContext;
import rsp.html.TagDefinition;
import rsp.html.Html;

public final class DefaultConnectionLostWidget {

    public static final String HTML;

    static {
        final DomTreePageRenderContext rc = new DomTreePageRenderContext();
        widget().accept(rc);
        HTML = rc.toString();
    }

    private static TagDefinition widget() {
        return Html.div( Html.style("position", "fixed"),
                    Html.style("top", "0"),
                    Html.style("left", "0"),
                    Html.style("right", "0"),
                    Html.style("background-color", "lightyellow"),
                    Html.style("border-bottom", "1px solid black"),
                    Html.style("padding", "10px"),
                    Html.text("Connection lost. Waiting to resume."));
    }
}
