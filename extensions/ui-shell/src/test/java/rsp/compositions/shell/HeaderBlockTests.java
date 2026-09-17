package rsp.compositions.shell;

import org.junit.jupiter.api.Test;
import rsp.authentication.Authentication;
import rsp.component.ComponentContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderBlockTests {

    @Test
    void initial_state_reads_authenticated_user_from_context() {
        HeaderBlock block = new HeaderBlock();
        HeaderView.HeaderViewState state = block.initStateSupplier().getState(null,
                new ComponentContext()
                        .with(Authentication.class, Authentication.authenticated("alice", "admin")));

        assertEquals("Header", block.title());
        assertTrue(state.authenticated());
        assertEquals("alice", state.username());
        assertNull(state.signOutHref());
    }

    @Test
    void initial_state_defaults_to_anonymous_user() {
        HeaderView.HeaderViewState state = new HeaderBlock().initStateSupplier()
                .getState(null, new ComponentContext());

        assertFalse(state.authenticated());
        assertEquals("", state.username());
    }

    @Test
    void configured_sign_out_link_is_presentation_data() {
        HeaderView.HeaderViewState state = new HeaderBlock("/auth/signout").initStateSupplier()
                .getState(null, new ComponentContext()
                        .with(Authentication.class, Authentication.authenticated("alice")));

        assertEquals("/auth/signout", state.signOutHref());
    }
}
