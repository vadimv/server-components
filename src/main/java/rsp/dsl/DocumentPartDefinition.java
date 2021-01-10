package rsp.dsl;

import rsp.page.RenderContext;

/**
 * Represents a building block of the domain-specific language.
 * For example a definition of a fragment of HTML or an event or a style or something else.
 */
public abstract class DocumentPartDefinition implements Comparable<DocumentPartDefinition> {
    public final DocumentPartKind kind;

    /**
     * The base class constructor. Creates a new instance of a definition of a document part.
     * @param kind the definition's type defining its order on sorting
     */
    public DocumentPartDefinition(DocumentPartKind kind) {
        this.kind = kind;
    }

    /**
     * An implementation of this method determines how its definition node is rendered to a virtual DOM tree.
     * @param renderContext the renderer
     */
    public abstract void accept(RenderContext renderContext);

    @Override
    public int compareTo(DocumentPartDefinition that) {
        return this.kind.compareTo(that.kind);
    }

    /**
     * Sets {@link DocumentPartDefinition} subclasses natural ordering within its parent node definition.
     */
    public static class DocumentPartKind implements Comparable<DocumentPartKind> {
        /**
         * Styles
         */
        public static final DocumentPartKind STYLE = new DocumentPartKind(0);
        /**
         * Attributes
         */
        public static final DocumentPartKind ATTR = new DocumentPartKind(1);
        /**
         * All the rest
         */
        public static final DocumentPartKind OTHER = new DocumentPartKind(2);

        private final int index;

        private DocumentPartKind(int index) {
            this.index = index;
        }

        @Override
        public int compareTo(DocumentPartKind that) {
            return Integer.compare(this.index, that.index);
        }
    }
}
