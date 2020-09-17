package rsp.dsl;

import rsp.EventContext;
import rsp.RenderContext;

import java.util.Objects;
import java.util.function.Consumer;

public class RefDefinition extends DocumentPartDefinition {
    private final Object parentRef;
    private final Object key;
    public RefDefinition() {
        this(new Object(), new Object());
    }

    private RefDefinition(Object parentRef, Object key) {
        super(DocumentPartKind.OTHER);
        this.parentRef = parentRef;
        this.key = key;
    }

    @Override
    public void accept(RenderContext renderContext) {
        renderContext.addRef(this);
    }

    public RefDefinition withKey(Object key) {
        return new RefDefinition(this, key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RefDefinition that = (RefDefinition) o;
        return parentRef.equals(that.parentRef) &&
                key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parentRef, key);
    }
}
