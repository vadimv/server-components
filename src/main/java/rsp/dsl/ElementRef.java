package rsp.dsl;

import rsp.component.ComponentRenderContext;

import java.util.Objects;

/**
 * A reference to an element.
 */
public final class ElementRef implements Definition {

    private final rsp.ref.ElementRef id;
    public ElementRef(final rsp.ref.ElementRef id) {
        this.id = Objects.requireNonNull(id);
    }

    @Override
    public boolean render(final ComponentRenderContext renderContext) {
        renderContext.addRef(id);
        return true;
    }

    public <K> KeyRef<K> withKey(final K key) {
        return new KeyRef<>(this, key);
    }

    public static class KeyRef<K> implements rsp.ref.ElementRef, Definition {
        private final ElementRef parentRef;
        private final K key;
        public KeyRef(final ElementRef parentRef, final K key) {
            super();
            this.parentRef = Objects.requireNonNull(parentRef);
            this.key = Objects.requireNonNull(key);
        }

        @Override
        public boolean render(final ComponentRenderContext renderContext) {
            renderContext.addRef(this);
            return true;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final KeyRef<?> keyRef = (KeyRef<?>) o;
            return parentRef.equals(keyRef.parentRef) && key.equals(keyRef.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parentRef, key);
        }
    }
}
