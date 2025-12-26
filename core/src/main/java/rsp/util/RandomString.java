package rsp.util;

import java.security.SecureRandom;

public final class RandomString {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final int length;

    private final SecureRandom rnd = new SecureRandom();

    public RandomString(final int length) {
        this.length = length;
    }

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
