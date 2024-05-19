package rsp.dom;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;


class StyleTests {
    @Test
    void should_comply_to_equals_hash_contract() {
        EqualsVerifier.forClass(Style.class).verify();
    }
}
