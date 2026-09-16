package rsp.util.html;

import java.util.Objects;

/**
 * Utility class for escaping HTML special characters to prevent XSS attacks.
 * 
 * This class provides a single method to escape HTML entities that could be
 * interpreted as markup or malicious code. The escaping is applied transparently
 * at the framework level to ensure security by default.
 * 
 * Escaped Characters
 * <ul>
 *   <li>{@code &} → {@code &amp;} (entity-based attacks)</li>
 *   <li>{@code <} → {@code &lt;} (tag opening)</li>
 *   <li>{@code >} → {@code &gt;} (tag closing)</li>
 *   <li>{@code "} → {@code &quot;} (attribute value breakout)</li>
 *   <li>{@code '} → {@code &#39;} (single-quote attribute breakout)</li>
 * </ul>
 * 
 * Implementation Notes
 * <p>
 * The escaping order is critical: the ampersand character must be escaped first
 * to prevent double-escaping. For example, if {@code <} is escaped to {@code &lt;}
 * before {@code &} is escaped, the result would be {@code &amp;lt;} instead of
 * {@code &lt;}.
 * </p>
 */
public final class HtmlEscape {
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private HtmlEscape() {
    }
    
    /**
     * Escapes HTML special characters in the given string to prevent XSS attacks.
     * 
     * <p>
     * Converts the following characters to their HTML entity equivalents:
     * </p>
     * <ul>
     *   <li>{@code &} → {@code &amp;}</li>
     *   <li>{@code <} → {@code &lt;}</li>
     *   <li>{@code >} → {@code &gt;}</li>
     *   <li>{@code "} → {@code &quot;}</li>
     *   <li>{@code '} → {@code &#39;}</li>
     * </ul>
     *
     * @param str the string to escape
     * @return the escaped string safe for use in HTML content
     * @throws NullPointerException if input is null
     */
    public static String escape(final String str) {
        Objects.requireNonNull(str);

        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
}
