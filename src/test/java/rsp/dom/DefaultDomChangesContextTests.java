package rsp.dom;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;


public class DefaultDomChangesContextTests {

    @Test
    public void should_comply_to_equals_hash_contract() {
        EqualsVerifier.forClass(DefaultDomChangesContext.Remove.class).verify();
        EqualsVerifier.forClass(DefaultDomChangesContext.RemoveStyle.class).verify();
        EqualsVerifier.forClass(DefaultDomChangesContext.RemoveAttr.class).verify();
        EqualsVerifier.forClass(DefaultDomChangesContext.SetStyle.class).verify();
        EqualsVerifier.forClass(DefaultDomChangesContext.SetAttr.class).verify();
        EqualsVerifier.forClass(DefaultDomChangesContext.Create.class).verify();
        EqualsVerifier.forClass(DefaultDomChangesContext.CreateText.class).verify();
    }
}
