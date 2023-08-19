package rsp.dom;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;


public class StyleTests {
    @Test
    public void should_comply_to_equals_hash_contract() {
        EqualsVerifier.forClass(Style.class).verify();
    }
}
