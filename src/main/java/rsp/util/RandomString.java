package rsp.util;

import java.security.SecureRandom;

public final class RandomString {

    private final int length;

    public RandomString(int length) {
        this.length = length;
    }

    private final static String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private SecureRandom rnd = new SecureRandom();

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
