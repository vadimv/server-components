package rsp.http.auth;

import rsp.url.Query;

import java.net.URI;
import java.util.List;

final class AuthenticationSupport {
    private AuthenticationSupport() {
    }

    static String loginRedirect(String loginPath, String target) {
        return loginPath + new Query(List.of(
                new Query.Parameter("redirect", safeLocalRedirect(target))));
    }

    /** Restricts a user-controlled post-authentication target to a same-origin path URL. */
    static String safeLocalRedirect(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return "/";
        }

        String candidate = redirect.trim();
        if (!candidate.startsWith("/")
                || candidate.startsWith("//")
                || candidate.indexOf('\\') >= 0
                || candidate.chars().anyMatch(ch -> Character.isISOControl(ch) || Character.isWhitespace(ch))) {
            return "/";
        }

        try {
            URI uri = URI.create(candidate);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawPath() == null) {
                return "/";
            }
            return candidate;
        } catch (IllegalArgumentException _) {
            return "/";
        }
    }
}
