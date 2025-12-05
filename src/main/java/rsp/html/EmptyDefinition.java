package rsp.html;

import rsp.component.ComponentRenderContext;

/**
 * The void definition, without any representation in the result DOM tree.
 */
final class EmptyDefinition implements Definition {
    /**
     * The default instance for reuse.
     */
    public static final EmptyDefinition INSTANCE = new EmptyDefinition();

    /**
     * Creates a new instance of an empty definition.
     */
    public EmptyDefinition() {
        super();
    }

    @Override
    public boolean render(final ComponentRenderContext renderContext) {
        return true;
    }
}
