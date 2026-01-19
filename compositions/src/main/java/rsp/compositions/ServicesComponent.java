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

            // Generic handling - contract enriches context with its own data
            ComponentContext enrichedContext = contract.enrichContext(context);

            // Add module-level configuration
            enrichedContext = enrichedContext
                    .with(ContextKeys.EDIT_MODE, module.editMode())
                    .with(ContextKeys.CREATE_TOKEN, module.createToken());

            // Handle EditView overlay modes (QUERY_PARAM and MODAL)
            if (contract instanceof ListViewContract<?>) {
                // Handle QUERY_PARAM mode: check for ?create=true
                if (module.editMode() == EditMode.QUERY_PARAM) {
                    String createParam = context.get(ContextKeys.URL_QUERY.with("create"));
                    boolean showCreate = "true".equalsIgnoreCase(createParam);

                    if (showCreate) {
                        // Instantiate overlay edit contract for create form
                        Class<? extends ViewContract> editContractClass = module.editContractClass();
                        if (editContractClass != null) {
                            enrichedContext = enrichedContext.with(ContextKeys.OVERLAY_CONTRACT, editContractClass);

                            // Instantiate and enrich context for overlay
                            ViewContract overlayContract = instantiateContractFromModule(
                                    module, editContractClass, enrichedContext);
                            if (overlayContract != null) {
                                enrichedContext = overlayContract.enrichContext(enrichedContext);
                                enrichedContext = enrichedContext.with(ContextKeys.OVERLAY_VIEW_CONTRACT, overlayContract);
                            }
                        }
                    }
                }

                // Handle MODAL mode: pre-resolve modal overlay contract (shown when openCreateModal fires)
                if (module.editMode() == EditMode.MODAL) {
                    Class<? extends ViewContract> editContractClass = module.editContractClass();
                    if (editContractClass != null) {
                        enrichedContext = enrichedContext.with(ContextKeys.MODAL_OVERLAY_CONTRACT, editContractClass);

                        // Pre-instantiate the edit contract for create mode
                        ViewContract modalContract = instantiateContractFromModule(
                                module, editContractClass, enrichedContext);
                        if (modalContract != null) {
                            enrichedContext = modalContract.enrichContext(enrichedContext);
                            enrichedContext = enrichedContext.with(ContextKeys.MODAL_OVERLAY_VIEW_CONTRACT, modalContract);
                        }
                    }
                }
            }

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
                ViewContract contract = placement.contractFactory().apply(lookup);
                // Register event handlers for this contract
                if (contract != null) {
                    contract.registerHandlers();
                }
                return contract;
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

    public record ServicesComponentState() {
    }
}
