package rsp.dsl;

import rsp.component.TreeBuilder;
import rsp.dom.XmlNs;

import java.util.Arrays;

/**
 * A definition of a tag that has a close element.
 * @see rsp.dsl.Html#head(Html.HeadType, Definition...)
 */
public final class PlainTag extends Tag {

    public PlainTag(final XmlNs ns, final String name, final Definition... children) {
        super(ns, name, children);
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        renderContext.openNode(ns, name, false);
        Arrays.stream(children).forEach(c -> c.render(renderContext));
        renderContext.closeNode(name, false);
    }
}
