package rsp.server;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static rsp.server.TestCollectingRemoteOut.*;

class TestCollectingRemoteOutTests {

    @Test
    void should_comply_to_equals_hash_contract_for_helper_classes() {
        EqualsVerifier.forClass(SetRenderNumOutMessage.class).verify();
        EqualsVerifier.forClass(ListenEventOutMessage.class).verify();
        EqualsVerifier.forClass(ForgetEventOutMessage.class).verify();
        EqualsVerifier.forClass(ExtractPropertyOutMessage.class).verify();
        EqualsVerifier.forClass(ModifyDomOutMessage.class).verify();
        EqualsVerifier.forClass(PushHistoryMessage.class).verify();
    }
}
