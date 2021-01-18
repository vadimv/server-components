package rsp.dom;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class VirtualDomPathTests {
    @Test
    public void should_comply_to_equals_hash_contract() {
        EqualsVerifier.forClass(VirtualDomPath.class).verify();
    }
}
