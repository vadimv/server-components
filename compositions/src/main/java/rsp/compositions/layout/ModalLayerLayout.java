package rsp.compositions.layout;

import rsp.component.Lookup;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ViewContract;
import rsp.dsl.Definition;

import static rsp.compositions.contract.EventKeys.HIDE;
import static rsp.dsl.Html.*;

/**
 * Modal overlay layout: backdrop + centered content.
 * <p>
 * Clicking the backdrop publishes a HIDE event for the contract.
 */
public final class ModalLayerLayout implements LayerLayout {
    @Override
    public Definition render(Component<?> content,
                             Class<? extends ViewContract> contractClass,
                             Lookup lookup) {
        return div(attr("class", "modal-overlay"),
                div(attr("class", "modal-backdrop"),
                        on("click", _ -> lookup.publish(HIDE, contractClass))),
                div(attr("class", "modal-content"),
                        content));
    }
}
