package rsp.compositions.layout;

import rsp.component.Lookup;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ViewContract;
import rsp.dsl.Definition;

/**
 * Strategy interface for rendering content within a scene layer.
 * <p>
 * Each implementation defines the visual arrangement for a layer above the base layout.
 * Examples: modal overlay, activities overview with thumbnails, slide-in panel.
 *
 * @see ModalLayerLayout
 */
@FunctionalInterface
public interface LayerLayout {
    /**
     * Render the layer content with appropriate visual structure.
     *
     * @param content       the resolved UI component for the contract
     * @param contractClass the contract class (for event targeting, e.g., HIDE)
     * @param lookup        for event publishing
     * @return the rendered layer definition
     */
    Definition render(Component<?> content,
                      Class<? extends ViewContract> contractClass,
                      Lookup lookup);
}
