package rsp.dsl;

import rsp.component.TreeBuilder;

/**
 * An empty definition, a part of a UI tree which is not rendered.
 */
public final class EmptyDefinition implements Definition {
    public static final EmptyDefinition INSTANCE = new EmptyDefinition();

    private EmptyDefinition() {
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        // do nothing
    }
}
