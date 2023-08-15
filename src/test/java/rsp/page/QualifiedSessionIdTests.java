package rsp.page;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;
import rsp.dom.Attribute;

public class QualifiedSessionIdTests {
    @Test
    public void should_comply_to_equals_hash_contract() {
        EqualsVerifier.forClass(QualifiedSessionId.class).withNonnullFields("deviceId", "sessionId").verify();
    }
}
