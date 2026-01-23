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
 * SceneComponent - Builds and stores the Scene for the current route.
 * <p>
 * This component:
 * <ol>
 *   <li>Reads modules and contract class from context (populated by RoutingComponent)</li>
 *   <li>Builds a Scene containing instantiated contracts in {@code initStateSupplier()}</li>
 *   <li>Stores the Scene in component state (contracts created once at mount)</li>
 *   <li>Enriches context with scene data for downstream UI components</li>
 * </ol>
 * <p>
 * Position in component chain: RoutingComponent → SceneComponent → UiManagementComponent
 * <p>
 * Benefits over transient context enrichment:
 * <ul>
 *   <li>Contracts instantiated once at mount, not every render cycle</li>
 *   <li>Scene is cacheable, testable, debuggable</li>
 *   <li>Explicit error handling via {@link Scene#isValid()}</li>
 * </ul>
 */
public class SceneComponent extends Component<Scene> {

    public SceneComponent() {
        super();
    }

    /**
     * Build the Scene at component mount time.
     * This is where contracts are instantiated and event handlers registered.
     */
    @Override
    public ComponentStateSupplier<Scene> initStateSupplier() {
        return (_, context) -> buildScene(context);
    }

    /**
     * Enrich context with scene data for downstream components.
     */
    @Override
    public BiFunction<ComponentContext, Scene, ComponentContext> subComponentsContext() {
        return (context, scene) -> {
            if (scene == null || !scene.isValid()) {
                return context; // Render will show error
            }

            ViewContract contract = scene.primaryContract();
            Module module = scene.module();

            // Let the contract enrich context with its data (items, schema, etc.)
            ComponentContext enrichedContext = contract.enrichContext(context);

            // Add module-level configuration
            enrichedContext = enrichedContext
                    .with(ContextKeys.EDIT_MODE, module.editMode())
                    .with(ContextKeys.CREATE_TOKEN, module.createToken());

            // Add modal overlay if present (for MODAL mode)
            if (scene.modalContract() != null) {
                Class<? extends ViewContract> modalContractClass = scene.modalContract().getClass();
                enrichedContext = enrichedContext.with(ContextKeys.MODAL_OVERLAY_CONTRACT, modalContractClass);
                enrichedContext = scene.modalContract().enrichContext(enrichedContext);
                enrichedContext = enrichedContext.with(ContextKeys.MODAL_OVERLAY_VIEW_CONTRACT, scene.modalContract());
            }

            return enrichedContext;
        };
    }

    @Override
    public ComponentView<Scene> componentView() {
        return _ -> scene -> {
            if (scene == null) {
                throw new IllegalStateException("Scene is null - check context setup");
            }

            if (scene.error() != null) {
                throw new IllegalStateException("Scene build failed", scene.error());
            }

            if (!scene.authorized()) {
                throw new AuthorizationException("Access denied: insufficient permissions");
            }

            return new UiManagementComponent();
        };
    }

    /**
     * Build the Scene from context.
     * Finds the module, instantiates contracts, checks authorization.
     */
    private Scene buildScene(ComponentContext context) {
        try {
            // Read from context (populated by RoutingComponent)
            List<Module> modules = context.get(ContextKeys.APP_MODULES);
            Class<? extends ViewContract> contractClass = context.get(ContextKeys.ROUTE_CONTRACT_CLASS);

            if (modules == null || contractClass == null) {
                return Scene.error(new IllegalStateException("Missing APP_MODULES or ROUTE_CONTRACT_CLASS in context"));
            }

            // Find module that has a ViewPlacement with this contract class
            Module module = findModuleWithContract(modules, contractClass);
            if (module == null) {
                return Scene.error(new IllegalStateException("No module found for contract: " + contractClass.getName()));
            }

            // Get the contract factory from the module and instantiate with context
            ViewContract contract = instantiateContractFromModule(module, contractClass, context);
            if (contract == null) {
                return Scene.error(new IllegalStateException("Contract not found in module: " + contractClass.getName()));
            }

            // Check authorization
            if (!contract.isAuthorized()) {
                return Scene.unauthorized(contract, module);
            }

            // Handle MODAL mode: pre-instantiate modal overlay contract
            ViewContract modalContract = null;
            if (module.editMode() == EditMode.MODAL) {
                Class<? extends ViewContract> editContractClass = module.editContractClass();
                // Only instantiate overlay if editContractClass exists and is different from primary
                if (editContractClass != null && !editContractClass.equals(contractClass)) {
                    modalContract = instantiateContractFromModule(module, editContractClass, context);
                }
            }

            return Scene.of(contract, module, modalContract);

        } catch (Exception e) {
            return Scene.error(e);
        }
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
}
