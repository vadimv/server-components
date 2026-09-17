package rsp.authentication;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationTests {
    @Test
    void represents_anonymous_and_authenticated_identities_without_contradictory_state() {
        Authentication anonymous = Authentication.anonymous();
        Authentication authenticated = Authentication.authenticated("alice", "admin", "editor", "admin");

        assertSame(anonymous, Authentication.anonymous());
        assertFalse(anonymous.isAuthenticated());
        assertEquals(Set.of(), anonymous.roles());
        assertTrue(authenticated.isAuthenticated());
        assertEquals("alice", authenticated.principal());
        assertEquals(Set.of("admin", "editor"), authenticated.roles());
        assertTrue(authenticated.hasRole("admin"));
        assertFalse(authenticated.hasRole("viewer"));
        assertThrows(IllegalArgumentException.class,
                () -> new Authentication(null, Set.of("admin")));
        assertThrows(NullPointerException.class,
                () -> Authentication.authenticated(null, "admin"));
    }

    @Test
    void copies_and_exposes_an_immutable_role_set() {
        Set<String> roles = new HashSet<>(Set.of("admin"));
        Authentication authentication = new Authentication("alice", roles);
        roles.add("editor");

        assertEquals(Set.of("admin"), authentication.roles());
        assertThrows(UnsupportedOperationException.class,
                () -> authentication.roles().add("viewer"));
    }
}
