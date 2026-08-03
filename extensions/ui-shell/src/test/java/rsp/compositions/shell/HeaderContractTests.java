package rsp.compositions.shell;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.compositions.contract.ContextKeys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderContractTests {

    @Test
    void initial_state_reads_authenticated_user_from_context() {
        HeaderContract contract = new HeaderContract();
        HeaderView.HeaderViewState state = contract.initStateSupplier().getState(null,
                new ComponentContext()
                        .with(ContextKeys.AUTH_AUTHENTICATED, Boolean.TRUE)
                        .with(ContextKeys.AUTH_USER, "alice"));

        assertEquals("Header", contract.title());
        assertTrue(state.authenticated());
        assertEquals("alice", state.username());
    }

    @Test
    void initial_state_defaults_to_anonymous_user() {
        HeaderView.HeaderViewState state = new HeaderContract().initStateSupplier()
                .getState(null, new ComponentContext());

        assertFalse(state.authenticated());
        assertEquals("", state.username());
    }
}
