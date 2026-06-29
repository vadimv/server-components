package rsp.util.json;

/**
 * Resource bounds enforced while parsing, to keep adversarial input from exhausting the stack or
 * heap. The bounds fall into two tiers:
 *
 * <ul>
 *   <li><b>Amplifiers</b> — small input, unbounded cost — are always meaningful:
 *     <ul>
 *       <li>{@code maxDepth}: nesting depth. Recursive-descent parsing turns each level into a stack
 *           frame, so {@code [[[[…} would otherwise raise {@link StackOverflowError}. The cap also
 *           protects recursive <em>consumers</em> of the tree (e.g. {@code toString}/{@code equals}),
 *           so keep it comfortably below the real stack capacity.</li>
 *       <li>{@code maxNumberExponent} / {@code maxNumberDigits}: a 12-character token such as
 *           {@code 1e1000000000} is cheap to construct as a {@link java.math.BigDecimal} but
 *           catastrophic to expand. These caps are checked <em>before</em> the {@code BigDecimal} is
 *           built.</li>
 *     </ul>
 *   </li>
 *   <li><b>Defense-in-depth</b> — linear in input, mostly redundant with a transport-level total-size
 *       cap, but cheap to enforce: {@code maxObjectEntries}, {@code maxArrayElements},
 *       {@code maxStringLength}, {@code maxInputLength}.</li>
 * </ul>
 *
 * @param maxDepth          maximum container nesting depth
 * @param maxNumberDigits   maximum count of significant digits in a number
 * @param maxNumberExponent maximum absolute value of a number's decimal exponent
 * @param maxObjectEntries  maximum name/value pairs in a single object
 * @param maxArrayElements  maximum elements in a single array
 * @param maxStringLength   maximum length of a single string value (after unescaping)
 * @param maxInputLength    maximum length of the whole input document
 */
public record JsonLimits(int maxDepth,
                         int maxNumberDigits,
                         int maxNumberExponent,
                         int maxObjectEntries,
                         int maxArrayElements,
                         int maxStringLength,
                         int maxInputLength) {

    public JsonLimits {
        requirePositive("maxDepth", maxDepth);
        requirePositive("maxNumberDigits", maxNumberDigits);
        requirePositive("maxNumberExponent", maxNumberExponent);
        requirePositive("maxObjectEntries", maxObjectEntries);
        requirePositive("maxArrayElements", maxArrayElements);
        requirePositive("maxStringLength", maxStringLength);
        requirePositive("maxInputLength", maxInputLength);
    }

    /**
     * Sensible bounds for trusted-but-untested input: generous enough never to reject real-world
     * data, tight enough to stop the amplification attacks.
     */
    public static final JsonLimits DEFAULT = new JsonLimits(
            512,                 // maxDepth — well below a default thread's stack capacity
            1_000,               // maxNumberDigits — a 1000-digit integer is a ~415-byte BigInteger
            10_000,              // maxNumberExponent — dwarfs double's ±308 range; far below the bomb
            1_000_000,           // maxObjectEntries
            1_000_000,           // maxArrayElements
            Integer.MAX_VALUE,   // maxStringLength — bounded in practice by maxInputLength
            Integer.MAX_VALUE);  // maxInputLength — bound total size at the transport instead

    /** A tighter profile for parsing fully untrusted input. */
    public static JsonLimits strict() {
        return new JsonLimits(64, 100, 1_000, 10_000, 10_000, 1_000_000, 10_000_000);
    }

    private static void requirePositive(final String name, final int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
    }
}
