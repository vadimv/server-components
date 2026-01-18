package rsp.compositions;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.ContextLookup;
import rsp.component.Lookup;
import rsp.component.Subscriber;
import rsp.component.definitions.Component;

import java.util.List;
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
            List<Module> modules = (List<Module>) context.get(ContextKeys.APP_MODULES);
            Class<? extends ViewContract> contractClass = context.get(ContextKeys.ROUTE_CONTRACT_CLASS);

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
            ComponentContext enrichedContext = context;

            if (contract instanceof ListViewContract<?> listContract) {
                // Fetch list data
                List<?> items = listContract.items();
                int page = listContract.page();
                String sort = listContract.sort();

                // Extract schema from items
                DataSchema schema = extractSchema(items, listContract);

                // Put data AND schema in context for UI components with "list." namespace
                enrichedContext = enrichedContext
                    .with(ContextKeys.LIST_ITEMS, items)
                    .with(ContextKeys.LIST_SCHEMA, schema)
                    .with(ContextKeys.LIST_PAGE, page)
                    .with(ContextKeys.LIST_SORT, sort)
                    .with(ContextKeys.EDIT_MODE, module.editMode())
                    .with(ContextKeys.CREATE_TOKEN, module.createToken());

                // Handle QUERY_PARAM mode: check for ?create=true
                if (module.editMode() == EditMode.QUERY_PARAM) {
                    String createParam = context.get(ContextKeys.URL_QUERY.with("create"));
                    boolean showCreate = "true".equalsIgnoreCase(createParam);

                    if (showCreate) {
                        // Set overlay contract for create form
                        Class<? extends EditViewContract<?>> editContractClass = module.editContractClass();
                        if (editContractClass != null) {
                            enrichedContext = enrichedContext.with(ContextKeys.OVERLAY_CONTRACT, editContractClass);

                            // Also instantiate the edit contract and populate its context
                            // This is needed for the overlay component to have proper data
                            EditViewContract<?> editContract = (EditViewContract<?>) instantiateContractFromModule(
                                    module, editContractClass, enrichedContext);
                            if (editContract != null) {
                                Object entity = editContract.item(); // null for create mode
                                DataSchema editSchema = editContract.schema();

                                // Use separate context keys for overlay data
                                // VIEW_CONTRACT stays as ListViewContract for the primary view
                                enrichedContext = enrichedContext
                                        .with(ContextKeys.EDIT_ENTITY, entity)
                                        .with(ContextKeys.EDIT_SCHEMA, editSchema)
                                        .with(ContextKeys.OVERLAY_VIEW_CONTRACT, editContract);
                            }
                        }
                    }
                }

                // Handle MODAL mode: pre-resolve modal overlay contract (shown when openCreateModal fires)
                if (module.editMode() == EditMode.MODAL) {
                    Class<? extends EditViewContract<?>> editContractClass = module.editContractClass();
                    if (editContractClass != null) {
                        enrichedContext = enrichedContext.with(ContextKeys.MODAL_OVERLAY_CONTRACT, editContractClass);

                        // Pre-instantiate the edit contract for create mode
                        EditViewContract<?> editContract = (EditViewContract<?>) instantiateContractFromModule(
                                module, editContractClass, enrichedContext);
                        if (editContract != null) {
                            Object entity = editContract.item(); // null for create mode
                            DataSchema editSchema = editContract.schema();

                            // Store modal overlay contract and data
                            enrichedContext = enrichedContext
                                    .with(ContextKeys.EDIT_ENTITY, entity)
                                    .with(ContextKeys.EDIT_SCHEMA, editSchema)
                                    .with(ContextKeys.MODAL_OVERLAY_VIEW_CONTRACT, editContract);
                        }
                    }
                }
            } else if (contract instanceof EditViewContract<?> editContract) {
                // Fetch entity to edit
                Object entity = editContract.item();
                DataSchema schema = editContract.schema();

                // Put entity AND schema in context for UI components with "edit." namespace
                enrichedContext = enrichedContext
                    .with(ContextKeys.EDIT_ENTITY, entity)
                    .with(ContextKeys.EDIT_SCHEMA, schema);
            }

            // Store the contract itself for UI components to access
            enrichedContext = enrichedContext.with(ContextKeys.VIEW_CONTRACT, contract);

            // Enrich context with business data
            return enrichedContext;
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
                // Create Lookup from context + event infrastructure
                Lookup lookup = createLookup(context);
                // Instantiate contract using factory with lookup
                return placement.contractFactory().apply(lookup);
            }
        }
        return null;
    }

    /**
     * Create a Lookup from ComponentContext for contract instantiation.
     * This bridges the framework infrastructure to the contract API.
     */
    private Lookup createLookup(ComponentContext context) {
        CommandsEnqueue commandsEnqueue = context.getRequired(CommandsEnqueue.class);
        Subscriber subscriber = context.getRequired(Subscriber.class);
        return new ContextLookup(context, commandsEnqueue, subscriber);
    }

    /**
     * Extract schema from items list.
     * If items are empty, returns empty schema.
     * Otherwise extracts schema from the first item (all items should have same type).
     *
     * @param items The items to extract schema from
     * @param contract The contract (for potential customization)
     * @return DataSchema with column definitions
     */
    private DataSchema extractSchema(List<?> items, ListViewContract<?> contract) {
        if (items == null || items.isEmpty()) {
            return new DataSchema(List.of());
        }

        // Extract base schema from first item
        DataSchema schema = DataSchema.fromFirstItem(items.get(0));

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
        DataSchema customizeSchema(DataSchema defaultSchema);
    }

    public record ServicesComponentState() {
    }
}
