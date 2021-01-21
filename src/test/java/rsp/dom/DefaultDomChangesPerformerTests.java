package rsp.dom;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class DefaultDomChangesPerformerTests {

    @Test
    public void should_comply_to_equals_hash_contract() {
        EqualsVerifier.forClass(DefaultDomChangesPerformer.Remove.class).verify();
        EqualsVerifier.forClass(DefaultDomChangesPerformer.RemoveStyle.class).verify();
        EqualsVerifier.forClass(DefaultDomChangesPerformer.RemoveAttr.class).verify();
        EqualsVerifier.forClass(DefaultDomChangesPerformer.SetStyle.class).verify();
        EqualsVerifier.forClass(DefaultDomChangesPerformer.SetAttr.class).verify();
        EqualsVerifier.forClass(DefaultDomChangesPerformer.Create.class).verify();
        EqualsVerifier.forClass(DefaultDomChangesPerformer.CreateText.class).verify();
    }
}
