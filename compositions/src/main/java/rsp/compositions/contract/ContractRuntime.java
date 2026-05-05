package rsp.compositions.contract;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ContextScope;
import rsp.component.Lookup;

import java.util.Map;
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
    /**
     * SHOW_DATA captured at instantiation time, re-injected into every replaced context
     * so on-demand contracts (instantiated via SHOW with a payload) keep their data
     * across re-renders. Empty when the contract was not instantiated via SHOW.
     */
    private final Map<String, Object> showData;
    private boolean destroyed;

    public ContractRuntime(final Class<? extends ViewContract> contractClass,
                           final ViewContract contract,
                           final ContextScope.Controller contextController) {
        this(contractClass, contract, contextController, Map.of());
    }

    ContractRuntime(final Class<? extends ViewContract> contractClass,
                    final ViewContract contract,
                    final ContextScope.Controller contextController,
                    final Map<String, Object> showData) {
        this.contractClass = Objects.requireNonNull(contractClass, "contractClass");
        this.contract = Objects.requireNonNull(contract, "contract");
        this.contextController = Objects.requireNonNull(contextController, "contextController");
        this.showData = (showData == null || showData.isEmpty()) ? Map.of() : Map.copyOf(showData);
    }

    static ContractRuntime instantiate(final Class<? extends ViewContract> contractClass,
                                       final Function<Lookup, ViewContract> factory,
                                       final ComponentContext context) {
        final ContextScope.Controller controller = ContextScope.controller(context);
        final Lookup lookup = LookupFactory.create(controller.scope(), context);
        return instantiate(contractClass, factory, controller, lookup, capturedShowData(context));
    }

    static ContractRuntime instantiate(final Class<? extends ViewContract> contractClass,
                                       final Function<Lookup, ViewContract> factory,
                                       final ComponentContext context,
                                       final CommandsEnqueue commandsEnqueue) {
        final ContextScope.Controller controller = ContextScope.controller(context);
        final Lookup lookup = LookupFactory.create(controller.scope(), context, commandsEnqueue);
        return instantiate(contractClass, factory, controller, lookup, capturedShowData(context));
    }

    private static ContractRuntime instantiate(final Class<? extends ViewContract> contractClass,
                                               final Function<Lookup, ViewContract> factory,
                                               final ContextScope.Controller controller,
                                               final Lookup lookup,
                                               final Map<String, Object> showData) {
        Objects.requireNonNull(contractClass, "contractClass");
        Objects.requireNonNull(factory, "factory");
        final ViewContract contract = factory.apply(lookup);
        if (contract == null) {
            controller.clear();
            return null;
        }
        contract.registerHandlers();
        return new ContractRuntime(contractClass, contract, controller, showData);
    }

    /**
     * Snapshot SHOW_DATA from the instantiation context. The returned map is the
     * runtime's persistent copy used to re-inject SHOW_DATA on every context replace.
     */
    private static Map<String, Object> capturedShowData(final ComponentContext context) {
        Map<String, Object> data = context.get(ContextKeys.SHOW_DATA);
        return data == null ? Map.of() : data;
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
        // Re-inject SHOW_DATA from instantiation time so on-demand contracts
        // (e.g., inline forms instantiated via SHOW with {id: ...}) keep their
        // payload across re-renders. Without this, every render replaces the
        // controller's context, dropping SHOW_DATA after the first render.
        ComponentContext effective = showData.isEmpty()
                ? context
                : context.with(ContextKeys.SHOW_DATA, showData);
        contextController.replace(effective);
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
