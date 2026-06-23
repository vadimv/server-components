package rsp.compositions.agent;

import rsp.compositions.contract.ContractAction;
import rsp.compositions.contract.ContractMetadata;

import rsp.compositions.contract.EditViewContract;
import rsp.compositions.contract.FormViewContract;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.contract.ViewContract;

import java.util.List;

/**
 * Profile of a contract's capabilities for agent discovery.
 * <p>
 * Combines structured metadata from {@link ViewContract#contractMetadata()}
 * with the declared action vocabulary from {@link ViewContract#agentActions()}.
 * <p>
 * The agent receives the metadata for reasoning (live state, schema)
 * and the actions for intent construction (what can be done).
 *
 * @param metadata      structured metadata (nullable — contract may not expose metadata)
 * @param actions       declared agent-invocable actions
 * @param contractClass the contract's class
 */
public record ContractProfile(ContractMetadata metadata,
                              List<ContractAction> actions,
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

        ContractMetadata metadata = contract.contractMetadata();
        List<ContractAction> actions = contract.agentActions();

        return new ContractProfile(metadata, actions, contract.getClass());
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
