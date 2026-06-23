package rsp.util;

import java.security.SecureRandom;

/**
 * A generator of random alfa-numeric strings.
 */
public final class RandomString {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final int length;

    private final SecureRandom rnd = new SecureRandom();

    /**
     * Creates a new instance of a fixed length random string generator.
     * @param length the length of the generated strings
     */
    public RandomString(final int length) {
        this.length = length;
    }

    /**
     * Generates a new random alfa-numeric string.
     * @return a random string of a fixed length
     */
    public String newString() {
        final StringBuilder sb = new StringBuilder(length);
        var i = 0;
        while (i < length) {
            sb.append(ALPHABET.charAt(rnd.nextInt(ALPHABET.length())));
            i += 1;
        }
        return sb.toString();
    }
}
