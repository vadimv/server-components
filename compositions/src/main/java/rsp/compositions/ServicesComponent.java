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

            // Get the contract factory from the module and instantiate with context
            ViewContract contract = instantiateContractFromModule(module, contractClass, context);
            if (contract == null) {
                throw new IllegalStateException("Contract not found in module: " + contractClass.getName());
            }

            // Check authorization
            if (!contract.isAuthorized()) {
                throw new AuthorizationException("Access denied: insufficient permissions for " + contractClass.getSimpleName());
            }

            // Call contract methods to get data (contract will call module internally)
            Map<String, Object> dataMap = new HashMap<>();

            if (contract instanceof ListViewContract<?> listContract) {
                // Fetch list data
                List<?> items = listContract.items();
                int page = listContract.page();
                String sort = listContract.sort();

                // Extract schema from items
                ListSchema schema = extractSchema(items, listContract);

                // Put data AND schema in context for UI components with "list." namespace
                dataMap.put("list.items", items);
                dataMap.put("list.schema", schema);
                dataMap.put("list.page", page);
                dataMap.put("list.sort", sort);
            } else if (contract instanceof EditViewContract<?> editContract) {
                // Fetch entity to edit
                Object entity = editContract.item();
                ListSchema schema = editContract.schema();

                // Put entity AND schema in context for UI components with "edit." namespace
                dataMap.put("edit.entity", entity);
                dataMap.put("edit.schema", schema);
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
                if (placement.contractClass().equals(contractClass) ||
                    contractClass.isAssignableFrom(placement.contractClass())) {
                    return module;
                }
            }
        }
        return null;
    }

    private ViewContract instantiateContractFromModule(Module module, Class<? extends ViewContract> contractClass,
                                                        ComponentContext context) {
        for (ViewPlacement placement : module.views()) {
            if (placement.contractClass().equals(contractClass) ||
                contractClass.isAssignableFrom(placement.contractClass())) {
                // Instantiate contract using factory with context
                return placement.contractFactory().apply(context);
            }
        }
        return null;
    }

    /**
     * Extract schema from items list.
     * If items are empty, returns empty schema.
     * Otherwise extracts schema from the first item (all items should have same type).
     *
     * @param items The items to extract schema from
     * @param contract The contract (for potential customization)
     * @return ListSchema with column definitions
     */
    private ListSchema extractSchema(List<?> items, ListViewContract<?> contract) {
        if (items == null || items.isEmpty()) {
            return new ListSchema(List.of());
        }

        // Extract base schema from first item
        ListSchema schema = ListSchema.fromFirstItem(items.get(0));

        // Allow contract to customize schema (optional)
        if (contract instanceof SchemaCustomizer customizer) {
            schema = customizer.customizeSchema(schema);
        }

        return schema;
    }

    /**
     * Optional interface for contracts that want to customize their schema presentation.
     */
    public interface SchemaCustomizer {
        ListSchema customizeSchema(ListSchema defaultSchema);
    }

    public record ServicesComponentState() {
    }
}
