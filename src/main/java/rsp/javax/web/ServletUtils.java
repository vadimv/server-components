package rsp.javax.web;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.Optional;

public class ServletUtils {
    public static Optional<Cookie> cookie(HttpServletRequest request, String name) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(name);
        final Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for(int i=0;i< cookies.length;i++) {
                if (name.equals(cookies[i].getName())){
                    return Optional.of(cookies[i]);
                }
            }
        }
        return Optional.empty();
    }
}
