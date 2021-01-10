package rsp.dsl;

import rsp.page.RenderContext;

/**
 * Represents a building block of the domain-specific language.
 * For example a definition of a fragment of HTML or an event or a style or something else.
 */
public abstract class DocumentPartDefinition implements Comparable<DocumentPartDefinition> {
    public final DocumentPartKind kind;

    /**
     * Creates a new instance of a DSL document part.
     * @param kind the definition's type defining its order on sorting
     */
    public DocumentPartDefinition(DocumentPartKind kind) {
        this.kind = kind;
    }

    public abstract void accept(RenderContext renderContext);

    @Override
    public int compareTo(DocumentPartDefinition that) {
        return this.kind.compareTo(that.kind);
    }

    /**
     * {@link DocumentPartDefinition} subclasses natural ordering.
     */
    public static class DocumentPartKind implements Comparable<DocumentPartKind> {

        public static final DocumentPartKind STYLE = new DocumentPartKind(0);
        public static final DocumentPartKind ATTR = new DocumentPartKind(1);
        public static final DocumentPartKind OTHER = new DocumentPartKind(2);

        private final int index;

        public DocumentPartKind(int index) {
            this.index = index;
        }

        @Override
        public int compareTo(DocumentPartKind that) {
            return Integer.compare(this.index, that.index);
        }
    }
}
