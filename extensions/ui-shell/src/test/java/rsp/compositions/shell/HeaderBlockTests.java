package rsp.compositions.shell;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.compositions.block.ContextKeys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderBlockTests {

    @Test
    void initial_state_reads_authenticated_user_from_context() {
        HeaderBlock block = new HeaderBlock();
        HeaderView.HeaderViewState state = block.initStateSupplier().getState(null,
                new ComponentContext()
                        .with(ContextKeys.AUTH_AUTHENTICATED, Boolean.TRUE)
                        .with(ContextKeys.AUTH_USER, "alice"));

        assertEquals("Header", block.title());
        assertTrue(state.authenticated());
        assertEquals("alice", state.username());
    }

    @Test
    void initial_state_defaults_to_anonymous_user() {
        HeaderView.HeaderViewState state = new HeaderBlock().initStateSupplier()
                .getState(null, new ComponentContext());

        assertFalse(state.authenticated());
        assertEquals("", state.username());
    }
}
