package rsp.compositions.contract;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ContextScope;
import rsp.component.Lookup;

import java.util.Objects;
import java.util.function.Function;

/**
 * Runtime wrapper for an active {@link ViewContract}.
 * <p>
 * The contract owns long-lived handlers and receives a scope-backed lookup. A
 * boundary component feeds fresh rendered context into the scope as the component
 * tree changes.
 */
public final class ContractRuntime {
    private final Class<? extends ViewContract> contractClass;
    private final ViewContract contract;
    private final ContextScope.Controller contextController;
    private boolean destroyed;

    public ContractRuntime(final Class<? extends ViewContract> contractClass,
                           final ViewContract contract,
                           final ContextScope.Controller contextController) {
        this.contractClass = Objects.requireNonNull(contractClass, "contractClass");
        this.contract = Objects.requireNonNull(contract, "contract");
        this.contextController = Objects.requireNonNull(contextController, "contextController");
    }

    static ContractRuntime instantiate(final Class<? extends ViewContract> contractClass,
                                       final Function<Lookup, ViewContract> factory,
                                       final ComponentContext context) {
        final ContextScope.Controller controller = ContextScope.controller(context);
        final Lookup lookup = LookupFactory.create(controller.scope(), context);
        return instantiate(contractClass, factory, controller, lookup);
    }

    static ContractRuntime instantiate(final Class<? extends ViewContract> contractClass,
                                       final Function<Lookup, ViewContract> factory,
                                       final ComponentContext context,
                                       final CommandsEnqueue commandsEnqueue) {
        final ContextScope.Controller controller = ContextScope.controller(context);
        final Lookup lookup = LookupFactory.create(controller.scope(), context, commandsEnqueue);
        return instantiate(contractClass, factory, controller, lookup);
    }

    private static ContractRuntime instantiate(final Class<? extends ViewContract> contractClass,
                                               final Function<Lookup, ViewContract> factory,
                                               final ContextScope.Controller controller,
                                               final Lookup lookup) {
        Objects.requireNonNull(contractClass, "contractClass");
        Objects.requireNonNull(factory, "factory");
        final ViewContract contract = factory.apply(lookup);
        if (contract == null) {
            controller.clear();
            return null;
        }
        contract.registerHandlers();
        return new ContractRuntime(contractClass, contract, controller);
    }

    public Class<? extends ViewContract> contractClass() {
        return contractClass;
    }

    public ViewContract contract() {
        return contract;
    }

    ContextScope.Controller contextController() {
        return contextController;
    }

    public ContextScope contextScope() {
        return contextController.scope();
    }

    public void replaceContext(final ComponentContext context) {
        contextController.replace(context);
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        contract.onDestroy();
        contextController.clear();
    }
}
