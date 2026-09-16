package rsp.http;

import java.time.Duration;
import java.util.Objects;

/** A response cookie serialized for a {@code Set-Cookie} header. */
public record SetCookie(Cookie cookie, String path, Duration maxAge, boolean secure,
                        boolean httpOnly, SameSite sameSite) {
    public enum SameSite { STRICT, LAX, NONE }

    public SetCookie {
        Objects.requireNonNull(cookie, "cookie");
        path = path == null ? null : Objects.requireNonNull(path);
        sameSite = sameSite == null ? null : sameSite;
    }

    public static SetCookie of(String name, String value) {
        return new SetCookie(new Cookie(name, value), null, null, false, false, null);
    }

    public SetCookie path(String value) {
        return new SetCookie(cookie, value, maxAge, secure, httpOnly, sameSite);
    }

    public SetCookie maxAge(Duration value) {
        return new SetCookie(cookie, path, value, secure, httpOnly, sameSite);
    }

    public SetCookie withSecure() {
        return new SetCookie(cookie, path, maxAge, true, httpOnly, sameSite);
    }

    public SetCookie withHttpOnly() {
        return new SetCookie(cookie, path, maxAge, secure, true, sameSite);
    }

    public SetCookie sameSite(SameSite value) {
        return new SetCookie(cookie, path, maxAge, secure, httpOnly, value);
    }

    public String headerValue() {
        StringBuilder result = new StringBuilder(cookie.name()).append('=').append(cookie.value());
        if (path != null) result.append("; Path=").append(path);
        if (maxAge != null) result.append("; Max-Age=").append(maxAge.toSeconds());
        if (secure) result.append("; Secure");
        if (httpOnly) result.append("; HttpOnly");
        if (sameSite != null) {
            String value = sameSite.name().charAt(0) + sameSite.name().substring(1).toLowerCase();
            result.append("; SameSite=").append(value);
        }
        return result.toString();
    }
}
