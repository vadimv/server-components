package rsp.page;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;
import static rsp.page.TestCollectingRemoteOut.*;

public class TestCollectingRemoteOutTests {

    @Test
    public void should_comply_to_equals_hash_contract_for_helper_classes() {
        EqualsVerifier.forClass(SetRenderNumOutMessage.class).verify();
        EqualsVerifier.forClass(ListenEventOutMessage.class).verify();
        EqualsVerifier.forClass(ForgetEventOutMessage.class).verify();
        EqualsVerifier.forClass(ExtractPropertyOutMessage.class).verify();
        EqualsVerifier.forClass(ModifyDomOutMessage.class).verify();
        EqualsVerifier.forClass(PushHistoryMessage.class).verify();
    }
}
