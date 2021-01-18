package rsp.util;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class Tuple2Tests {
    @Test
    public void should_comply_to_equals_hash_contract() {
        EqualsVerifier.forClass(Tuple2.class).verify();
    }
}
