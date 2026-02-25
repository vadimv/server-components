package rsp.compositions.agent;

import rsp.component.EventKey;
import rsp.compositions.contract.EditViewContract;
import rsp.compositions.contract.FormViewContract;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.contract.ViewContract;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * Profile of a contract's capabilities for agent discovery.
 * <p>
 * Combines the natural-language description from {@link AgentInfo#agentDescription()}
 * with reflection on public methods and declared {@link EventKey} fields.
 *
 * @param description natural-language description (null if contract doesn't implement AgentInfo)
 * @param methods     public methods declared by the contract (excluding Object and ViewContract)
 * @param eventKeys   declared EventKey fields (the contract's action vocabulary)
 * @param contractClass the contract's class
 */
public record ContractProfile(String description,
                               List<MethodInfo> methods,
                               List<EventKeyInfo> eventKeys,
                               Class<?> contractClass) {

    public record MethodInfo(String name, Class<?> returnType, Class<?>[] parameterTypes) {}

    public record EventKeyInfo(String name, Class<?> eventKeyType) {}

    /**
     * Build a profile from a contract instance.
     *
     * @param contract the contract to profile
     * @return the profile
     */
    public static ContractProfile of(ViewContract contract) {
        if (contract == null) {
            return new ContractProfile(null, List.of(), List.of(), Void.class);
        }

        String description = contract instanceof AgentInfo info
                ? info.agentDescription()
                : null;

        List<MethodInfo> methods = Arrays.stream(contract.getClass().getMethods())
                .filter(m -> !m.getDeclaringClass().equals(Object.class))
                .filter(m -> !m.getDeclaringClass().equals(ViewContract.class))
                .map(m -> new MethodInfo(m.getName(), m.getReturnType(), m.getParameterTypes()))
                .toList();

        List<EventKeyInfo> eventKeys = collectEventKeys(contract.getClass());

        return new ContractProfile(description, methods, eventKeys, contract.getClass());
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

    private static List<EventKeyInfo> collectEventKeys(Class<?> clazz) {
        return Arrays.stream(clazz.getFields())
                .filter(f -> EventKey.class.isAssignableFrom(f.getType()))
                .filter(f -> Modifier.isStatic(f.getModifiers()) && Modifier.isPublic(f.getModifiers()))
                .map(f -> new EventKeyInfo(f.getName(), f.getType()))
                .toList();
    }
}
