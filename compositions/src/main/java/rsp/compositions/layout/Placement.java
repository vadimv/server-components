package rsp.compositions.layout;

import java.util.Objects;

/**
 * Preferred visual placement for a contract.
 * <p>
 * Placements are layout hints. A resolver may later override them with user
 * preferences, fixed framework rules, or safety constraints.
 *
 * @param kind the broad placement kind
 * @param slot the layout slot name for inline placements
 */
public record Placement(Kind kind, String slot) {
    public static final Inline INLINE = new Inline();
    public static final Placement MODAL = new Placement(Kind.MODAL, "modal");

    public Placement {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(slot, "slot");
    }

    public enum Kind {
        INLINE,
        MODAL
    }

    public boolean isInline() {
        return kind == Kind.INLINE;
    }

    public boolean isModal() {
        return kind == Kind.MODAL;
    }

    public static final class Inline {
        private Inline() {}

        public Placement primary() {
            return new Placement(Kind.INLINE, "primary");
        }
    }
}
