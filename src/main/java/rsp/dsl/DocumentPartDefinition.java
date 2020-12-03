package rsp.dsl;

import rsp.services.RenderContext;

public abstract class DocumentPartDefinition implements Comparable<DocumentPartDefinition> {
    public final DocumentPartKind kind;

    protected DocumentPartDefinition(DocumentPartKind kind) {
        this.kind = kind;
    }

    public abstract void accept(RenderContext renderContext);

    public int compareTo(DocumentPartDefinition that) {
        return this.kind.compareTo(that.kind);
    }

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
