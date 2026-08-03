package rsp.compositions.contract;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable scene data that selects one contract component instance.
 *
 * A descriptor deliberately contains no live contract or lookup. The component
 * tree creates the runtime only when this descriptor's branch is mounted.
 */
public record ContractDescriptor(Class<? extends Contract> contractClass,
                                 Map<String, Object> showData,
                                 long instanceId) {
    private static final AtomicLong NEXT_INSTANCE_ID = new AtomicLong();

    public ContractDescriptor {
        Objects.requireNonNull(contractClass, "contractClass");
        showData = showData == null || showData.isEmpty() ? Map.of() : Map.copyOf(showData);
    }

    public static ContractDescriptor forContract(Class<? extends Contract> contractClass,
                                                 Map<String, Object> showData) {
        return new ContractDescriptor(contractClass, showData, NEXT_INSTANCE_ID.incrementAndGet());
    }

}
