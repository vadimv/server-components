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
 * Native modal dialog layout: backdrop + centered content with a close (X) button.
 * <p>
 * Escape, clicking the backdrop, and clicking the close button publish a HIDE
 * event for the block. The client promotes dialogs marked with
 * {@code data-rsp-auto-modal} into the top layer after their listeners arrive.
 */
public final class ModalLayerLayout implements LayerLayout {
    @Override
    public Definition resolve(Component<?, ?> content,
                              Class<? extends Block<?, ?>> blockClass,
                              Lookup lookup) {
        return resolve(content, blockClass, blockClass, lookup);
    }

    @Override
    public Definition resolve(Component<?, ?> content,
                              Object blockKey,
                              Class<? extends Block<?, ?>> blockClass,
                              Lookup lookup) {
        return dialog(
                attr("class", "modal-overlay"),
                attr("data-rsp-auto-modal", "true"),
                attr("aria-label", dialogLabel(blockClass)),
                on("cancel", _ -> lookup.publish(HIDE, blockKey)),
                div(attr("class", "modal-backdrop"),
                        on("click", _ -> lookup.publish(HIDE, blockKey))),
                div(attr("class", "modal-content"),
                        closeButton(blockKey, lookup),
                        content));
    }

    private static Definition closeButton(Object blockKey, Lookup lookup) {
        return form(
                attr("method", "dialog"),
                attr("class", "modal-close-form"),
                button(
                        attr("type", "submit"),
                        attr("class", "modal-close"),
                        attr("aria-label", "Close"),
                        on("click", _ -> lookup.publish(HIDE, blockKey)),
                        xIcon()));
    }

    private static String dialogLabel(Class<? extends Block<?, ?>> blockClass) {
        String name = blockClass.getSimpleName().replaceFirst("Block$", "");
        String readable = name.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        return readable.isBlank() ? "Dialog" : readable;
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
