package rsp.util.json;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;


public class JsonDataTypeTests {
    @Test
    public void should_comply_to_equals_hash_contract_string() {
        EqualsVerifier.forClass(JsonDataType.String.class).verify();
    }

    @Test
    public void should_comply_to_equals_hash_contract_number() {
        EqualsVerifier.forClass(JsonDataType.Number.class).verify();
    }

    @Test
    public void should_comply_to_equals_hash_contract_boolean() {
        EqualsVerifier.forClass(JsonDataType.Boolean.class).verify();
    }

    @Test
    public void should_comply_to_equals_hash_contract_object() {
        EqualsVerifier.forClass(JsonDataType.Object.class).verify();
    }

    @Test
    public void should_comply_to_equals_hash_contract_array() {
        EqualsVerifier.forClass(JsonDataType.Array.class).verify();
    }


}
