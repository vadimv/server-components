package rsp.compositions;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * ServicesComponent - The "External World IO" layer.
 * <p>
 * This component:
 * 1. Reads modules and contract class from context
 * 2. Finds the module that has the requested contract
 * 3. Gets the contract instance from that module
 * 4. Sets the contract's context so it can resolve query params
 * 5. Calls contract methods to fetch data (contract calls module which calls services)
 * 6. Populates context with the data for downstream UI components
 * <p>
 * This is where the bridge to the external world happens!
 */
public class ServicesComponent extends Component<ServicesComponent.ServicesComponentState> {

    /**
     * Default constructor - reads everything from ComponentContext.
     * This is a generic framework component.
     */
    public ServicesComponent() {
        super();
    }

    @Override
    public ComponentStateSupplier<ServicesComponentState> initStateSupplier() {
        return (_, _) -> new ServicesComponentState();
    }

    /**
     * Enrich context with business data.
     * This is where services are called via modules and contracts!
     */
    @Override
    public BiFunction<ComponentContext, ServicesComponentState, ComponentContext> subComponentsContext() {
        return (context, state) -> {
            // Read from context (populated by RoutingComponent)
            @SuppressWarnings("unchecked")
            List<Module> modules = (List<Module>) context.getAttribute("app.modules");
            @SuppressWarnings("unchecked")
            Class<? extends ViewContract> contractClass = (Class<? extends ViewContract>) context.getAttribute("route.contractClass");

            if (modules == null || contractClass == null) {
                return context; // Graceful degradation
            }

            // Find module that has a ViewPlacement with this contract class
            Module module = findModuleWithContract(modules, contractClass);
            if (module == null) {
                throw new IllegalStateException("No module found for contract: " + contractClass.getName());
            }

            // Get the contract instance from the module
            ViewContract contract = getContractFromModule(module, contractClass);
            if (contract == null) {
                throw new IllegalStateException("Contract not found in module: " + contractClass.getName());
            }

            // Set contract's context so it can resolve query params
            contract.context = context;

            // Call contract methods to get data (contract will call module internally)
            Map<String, Object> dataMap = new HashMap<>();

            if (contract instanceof ListViewContract<?> listContract) {
                // Fetch list data
                List<?> items = listContract.items();
                int page = listContract.page();
                String sort = listContract.sort();

                // Put data in context for UI components with "list." namespace
                dataMap.put("list.items", items);
                dataMap.put("list.page", page);
                dataMap.put("list.sort", sort);
            }

            // Store the contract itself for UI components to access
            dataMap.put("view.contract", contract);

            // Enrich context with business data
            return context.with(dataMap);
        };
    }

    @Override
    public ComponentView<ServicesComponentState> componentView() {
        // UiManagementComponent has default constructor - reads from context
        return _ -> _ -> new UiManagementComponent();
    }

    private Module findModuleWithContract(List<Module> modules, Class<? extends ViewContract> contractClass) {
        for (Module module : modules) {
            for (ViewPlacement placement : module.views()) {
                if (contractClass.isInstance(placement.contract())) {
                    return module;
                }
            }
        }
        return null;
    }

    private ViewContract getContractFromModule(Module module, Class<? extends ViewContract> contractClass) {
        for (ViewPlacement placement : module.views()) {
            if (contractClass.isInstance(placement.contract())) {
                return placement.contract();
            }
        }
        return null;
    }

    public record ServicesComponentState() {
    }
}
