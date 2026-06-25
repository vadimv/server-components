package rsp.compositions.shell;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.compositions.contract.ContextKeys;

import static org.junit.jupiter.api.Assertions.*;

class HeaderContractTests {

    @Test
    void enriches_context_with_auth_status_when_authenticated() {
        TestLookup lookup = new TestLookup()
                .withData(ContextKeys.AUTH_AUTHENTICATED, Boolean.TRUE)
                .withData(ContextKeys.AUTH_USER, "alice");
        HeaderContract contract = new HeaderContract(lookup);

        assertEquals("Header", contract.title());

        ComponentContext context = contract.enrichContext(new ComponentContext());
        assertEquals(Boolean.TRUE, context.get(HeaderContract.HEADER_AUTHENTICATED));
        assertEquals("alice", context.get(HeaderContract.HEADER_USERNAME));
    }

    @Test
    void defaults_to_unauthenticated_with_blank_username() {
        HeaderContract contract = new HeaderContract(new TestLookup());

        ComponentContext context = contract.enrichContext(new ComponentContext());
        assertEquals(Boolean.FALSE, context.get(HeaderContract.HEADER_AUTHENTICATED));
        assertEquals("", context.get(HeaderContract.HEADER_USERNAME));
    }
}
