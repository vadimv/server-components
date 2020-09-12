package rsp;

import rsp.dsl.TagDefinition;
import rsp.dsl.Html;

public class DefaultConnectionLostWidget {

    public static final String HTML;

    static {
        final RenderContext rc = new XhtmlRenderContext(TextPrettyPrinting.NO_PRETTY_PRINTING, "");
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
