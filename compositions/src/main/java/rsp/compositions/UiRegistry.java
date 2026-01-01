package rsp.compositions;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class UiRegistry {
    private final Map<Class<? extends ListView>, Supplier<? extends ListView>> components = new HashMap<>();

    public <T extends ViewContract> UiRegistry register(Class contractClass, Supplier<? extends ListView> concreteComponent) {
        components.put(contractClass, concreteComponent);
        return this;
    }

}
