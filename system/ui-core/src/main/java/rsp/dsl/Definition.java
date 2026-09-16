package rsp.dsl;

import rsp.component.TreeBuilder;

/**
 * A building block of a UI definition.
 */
public interface Definition {
    /**
     * Renders this definition to a tree builder.
     * @param renderContext the tree builder to render to, must not be null
     */
    void render(final TreeBuilder renderContext);
}
