package rsp.dsl;

import rsp.component.TreeBuilder;

import java.util.Objects;

/**
 * A keyed-identity DSL directive. It tags its parent element with a stable identity used for
 * keyed list diffing: when every child of a parent carries a key, the diff matches children
 * across renders by key instead of by sibling position, so list rotations (prepend, append,
 * insert, remove, reorder) avoid rewriting unchanged nodes and preserve their DOM identity.
 *
 * <p>The encoded segment is {@code "kn<decimal>"} for numeric keys and {@code "ks<escaped>"} for
 * string keys, where the string form has the path separator {@code _} and the escape char
 * {@code %} percent-encoded. The numeric/string prefixes ensure {@code key(42L)} and
 * {@code key("42")} never collide.
 *
 * <p>The caller owns the stability and uniqueness contract: a key must be derived from a stable
 * entity identifier (an id field, a sequence number) so the same logical element produces the
 * same key across renders, and keys must be unique among siblings.
 *
 * @param segment the encoded, path-safe key segment
 */
public record Key(String segment) implements Definition {

    public Key {
        Objects.requireNonNull(segment, "Key segment cannot be null");
    }

    public static Key of(final long value) {
        return new Key("kn" + value);
    }

    public static Key of(final String value) {
        Objects.requireNonNull(value, "Key value cannot be null");
        return new Key("ks" + escape(value));
    }

    /**
     * Percent-encodes everything outside an unreserved whitelist ({@code A-Z a-z 0-9 - .}), encoding
     * the UTF-8 bytes of other characters. This makes the segment safe in every place it travels:
     * an HTML attribute value ({@code data-rsp-key="..."}), the wire-protocol id string, the
     * {@code _}-separated path, and the client's element-id map. In particular it neutralizes
     * {@code "}, {@code <}, {@code >}, {@code &} and the path separator {@code _}, so a key derived
     * from untrusted input cannot break out of the attribute or the message.
     */
    private static String escape(final String value) {
        final byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final StringBuilder sb = new StringBuilder(bytes.length);
        for (final byte raw : bytes) {
            final int c = raw & 0xFF;
            final boolean unreserved = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '.';
            if (unreserved) {
                sb.append((char) c);
            } else {
                sb.append('%');
                sb.append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return sb.toString();
    }

    @Override
    public void render(final TreeBuilder renderContext) {
        renderContext.setKey(segment);
    }
}
