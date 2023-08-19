package rsp.dom;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;


public class XmlNsTests {
    @Test
    public void should_comply_to_equals_hash_contract() {
        EqualsVerifier.forClass(XmlNs.class).verify();
    }
}
