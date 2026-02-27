package rsp.compositions.agent;

import rsp.compositions.contract.EditViewContract;
import rsp.compositions.contract.FormViewContract;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.contract.ViewContract;

import java.util.List;

/**
 * Profile of a contract's capabilities for agent discovery.
 * <p>
 * Combines the natural-language description from {@link AgentInfo#agentDescription()}
 * with the declared action vocabulary from {@link ViewContract#agentActions()}.
 * <p>
 * The agent receives the description for reasoning (live state, visible items)
 * and the actions for intent construction (what can be done).
 *
 * @param description   natural-language description (null if contract doesn't implement AgentInfo)
 * @param actions       declared agent-invocable actions
 * @param contractClass the contract's class
 */
public record ContractProfile(String description,
                               List<AgentAction> actions,
                               Class<?> contractClass) {

    /**
     * Build a profile from a contract instance.
     *
     * @param contract the contract to profile
     * @return the profile
     */
    public static ContractProfile of(ViewContract contract) {
        if (contract == null) {
            return new ContractProfile(null, List.of(), Void.class);
        }

        String description = contract instanceof AgentInfo info
                ? info.agentDescription()
                : null;

        List<AgentAction> actions = contract.agentActions();

        return new ContractProfile(description, actions, contract.getClass());
    }

    /**
     * Check if this profile represents a list contract.
     */
    public boolean isList() {
        return ListViewContract.class.isAssignableFrom(contractClass);
    }

    /**
     * Check if this profile represents an edit contract.
     */
    public boolean isEdit() {
        return EditViewContract.class.isAssignableFrom(contractClass);
    }

    /**
     * Check if this profile represents a form contract (edit or create).
     */
    public boolean isForm() {
        return FormViewContract.class.isAssignableFrom(contractClass);
    }
}
