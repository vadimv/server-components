package rsp.compositions.layout;

import rsp.compositions.block.Block;

import rsp.component.Lookup;
import rsp.component.definitions.Component;
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
     * Resolve and render the layer content with appropriate visual structure.
     *
     * @param content       the resolved UI component for the block
     * @param blockClass the concrete block class used for presentation metadata
     * @param lookup        for event publishing
     * @return the rendered layer definition
     */
    Definition resolve(Component<?, ?> content,
                       Class<? extends Block<?, ?>> blockClass,
                       Lookup lookup);

    /**
     * Resolve a layer with its configured binding identity. Existing layouts
     * receive the concrete class; key-aware layouts may override this overload.
     */
    default Definition resolve(Component<?, ?> content,
                               Object blockKey,
                               Class<? extends Block<?, ?>> blockClass,
                               Lookup lookup) {
        return resolve(content, blockClass, lookup);
    }
}
