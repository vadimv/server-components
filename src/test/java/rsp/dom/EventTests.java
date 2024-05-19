package rsp.dom;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;


class EventTests {

    @Test
    void should_comply_to_equals_hash_contract() {
        EqualsVerifier.forClass(Event.class).withIgnoredFields("eventHandler").verify();
    }

    @Test
    void should_comply_to_equals_hash_contract_for_event_target() {
        EqualsVerifier.forClass(Event.Target.class).verify();
    }

    @Test
    void should_comply_to_equals_hash_contract_for_throttle_modifier() {
        EqualsVerifier.forClass(Event.ThrottleModifier.class).verify();
    }

    @Test
    void should_comply_to_equals_hash_contract_for_debounce_modifier() {
        EqualsVerifier.forClass(Event.DebounceModifier.class).verify();
    }
}
