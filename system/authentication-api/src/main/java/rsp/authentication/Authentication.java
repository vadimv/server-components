package rsp.authentication;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/** Immutable identity established for one request or page session. */
public record Authentication(Object principal, Set<String> roles) {
    private static final Authentication ANONYMOUS = new Authentication(null, Set.of());

    public Authentication {
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        if (principal == null && !roles.isEmpty()) {
            throw new IllegalArgumentException("Anonymous authentication cannot have roles");
        }
    }

    /** Returns the shared anonymous identity. */
    public static Authentication anonymous() {
        return ANONYMOUS;
    }

    /** Creates an authenticated identity with an application-defined principal. */
    public static Authentication authenticated(Object principal, String... roles) {
        Objects.requireNonNull(roles, "roles");
        return new Authentication(
                Objects.requireNonNull(principal, "principal"),
                Set.copyOf(Arrays.asList(roles.clone())));
    }

    public boolean isAuthenticated() {
        return principal != null;
    }

    public boolean hasRole(String role) {
        return roles.contains(Objects.requireNonNull(role, "role"));
    }
}
