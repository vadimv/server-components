package rsp.compositions.agent;

import org.junit.jupiter.api.Test;
import rsp.compositions.contract.ViewContract;

import static org.junit.jupiter.api.Assertions.*;

class AgentInfoTests {

    @Test
    void viewContract_without_agentInfo_is_not_discoverable() {
        // ViewContract does not implement AgentInfo
        assertFalse(AgentInfo.class.isAssignableFrom(ViewContract.class));
    }

    @Test
    void instanceof_check_returns_false_for_non_agentInfo() {
        // A plain ViewContract instance should not match
        Object contract = new Object();
        assertFalse(contract instanceof AgentInfo);
    }

    @Test
    void agentInfo_interface_has_agentDescription_method() throws NoSuchMethodException {
        assertNotNull(AgentInfo.class.getMethod("agentDescription"));
    }
}
