package rsp.dsl;

import rsp.component.TreeBuilder;

import java.util.Objects;

/**
 * A reference to a DOM element definition.
 * @param ref an element's reference
 */
public record ElementRef(rsp.ref.ElementRef ref) implements Definition {

    public ElementRef {
        Objects.requireNonNull(ref);
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        renderContext.addRef(ref);
    }
}
