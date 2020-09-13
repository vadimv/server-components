package rsp.util;

public class UnescapeJsonUtils {
    public static String unescape(String s) {
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        final int len = s.length() - 1;
        while (i < len) {
            final char c = s.charAt(i);
            int charsConsumed = 0;
            if (c != '\\') {
                charsConsumed = 1;
                sb.append(c);
            } else {
                charsConsumed = 2;
                switch (s.charAt(i + 1)) {
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        final String code = s.substring(i + 2, i + 6);
                        charsConsumed = 6;
                        sb.append((char) Integer.parseInt(code, 16));
                        break;
                }
            }
            i += charsConsumed;
        }
        return sb.toString();
    }
}
