package rsp.compositions.layout;

import rsp.compositions.block.Block;

import rsp.component.Lookup;
import rsp.component.definitions.Component;
import rsp.dom.XmlNs;
import rsp.dsl.Definition;
import rsp.dsl.PlainTag;

import static rsp.compositions.block.EventKeys.HIDE;
import static rsp.dsl.Html.*;

/**
 * Modal overlay layout: backdrop + centered content with a close (X) button.
 * <p>
 * Both clicking the backdrop and clicking the close button publish a HIDE
 * event for the block.
 */
public final class ModalLayerLayout implements LayerLayout {
    @Override
    public Definition resolve(Component<?, ?> content,
                              Class<? extends Block<?, ?>> blockClass,
                              Lookup lookup) {
        return div(attr("class", "modal-overlay"),
                div(attr("class", "modal-backdrop"),
                        on("click", _ -> lookup.publish(HIDE, blockClass))),
                div(attr("class", "modal-content"),
                        closeButton(blockClass, lookup),
                        content));
    }

    private static Definition closeButton(Class<? extends Block<?, ?>> blockClass, Lookup lookup) {
        return button(
                attr("type", "button"),
                attr("class", "modal-close"),
                attr("aria-label", "Close"),
                on("click", _ -> lookup.publish(HIDE, blockClass)),
                xIcon());
    }

    private static Definition xIcon() {
        return new PlainTag(XmlNs.svg, "svg",
                attr("class", "modal-close-icon"),
                attr("viewBox", "0 0 24 24"),
                attr("width", "20"),
                attr("height", "20"),
                attr("fill", "none"),
                attr("stroke", "currentColor"),
                attr("stroke-width", "2"),
                attr("stroke-linecap", "round"),
                attr("aria-hidden", "true"),
                new PlainTag(XmlNs.svg, "line",
                        attr("x1", "6"), attr("y1", "6"),
                        attr("x2", "18"), attr("y2", "18")),
                new PlainTag(XmlNs.svg, "line",
                        attr("x1", "6"), attr("y1", "18"),
                        attr("x2", "18"), attr("y2", "6")));
    }
}
