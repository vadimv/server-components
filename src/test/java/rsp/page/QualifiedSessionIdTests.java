package rsp.page;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class QualifiedSessionIdTests {
    @Test
    void should_comply_to_equals_hash_contract() {
        EqualsVerifier.forClass(QualifiedSessionId.class).withNonnullFields("deviceId", "sessionId").verify();
    }
}
