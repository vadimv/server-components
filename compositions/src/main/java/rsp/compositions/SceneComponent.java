package rsp.compositions;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.ContextLookup;
import rsp.component.Lookup;
import rsp.component.Subscriber;
import rsp.component.definitions.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Slot-based overlay resolution:
 * When the primary contract has Slot.PRIMARY, any Slot.OVERLAY contracts in the same
 * module are pre-instantiated for use as popup/modal overlays.
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
    public java.util.function.BiFunction<ComponentContext, Scene, ComponentContext> subComponentsContext() {
        return (context, scene) -> {
            if (scene == null || !scene.isValid()) {
                return context; // Render will show error
            }

            ViewContract contract = scene.primaryContract();

            // Let the contract enrich context with its data (items, schema, etc.)
            ComponentContext enrichedContext = contract.enrichContext(context);

            // Add overlay contracts to context if present
            if (scene.hasOverlays()) {
                enrichedContext = enrichedContext.with(ContextKeys.OVERLAY_CONTRACTS, scene.overlayContracts());

                // Enrich context with each overlay contract's data
                for (ViewContract overlay : scene.overlayContracts().values()) {
                    enrichedContext = overlay.enrichContext(enrichedContext);
                    // Store the contract instance for event handling
                    enrichedContext = enrichedContext.with(
                            ContextKeys.OVERLAY_VIEW_CONTRACT.with(overlay.getClass().getName()),
                            overlay);
                }
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
     * Uses Slot-based resolution for overlays.
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
            ViewPlacement primaryPlacement = module.placementFor(contractClass);
            if (primaryPlacement == null) {
                return Scene.error(new IllegalStateException("Contract not found in module: " + contractClass.getName()));
            }

            ViewContract contract = instantiatePlacement(primaryPlacement, context);
            if (contract == null) {
                return Scene.error(new IllegalStateException("Failed to instantiate contract: " + contractClass.getName()));
            }

            // Check authorization
            if (!contract.isAuthorized()) {
                return Scene.unauthorized(contract, module);
            }

            // Slot-based overlay resolution:
            // If primary is PRIMARY slot, pre-instantiate any OVERLAY contracts
            Map<Class<? extends ViewContract>, ViewContract> overlayContracts = Map.of();
            Slot primarySlot = primaryPlacement.slot();

            if (primarySlot == Slot.PRIMARY) {
                List<ViewPlacement> overlayPlacements = module.placementsForSlot(Slot.OVERLAY);
                if (!overlayPlacements.isEmpty()) {
                    overlayContracts = new HashMap<>();
                    // Create overlay context with IS_OVERLAY_MODE = true
                    ComponentContext overlayContext = context.with(ContextKeys.IS_OVERLAY_MODE, true);
                    for (ViewPlacement overlayPlacement : overlayPlacements) {
                        // Validate contract class is not null
                        Class<? extends ViewContract> overlayClass = overlayPlacement.contractClass();
                        if (overlayClass == null) {
                            throw new IllegalStateException("ViewPlacement has null contractClass in module: " + module.getClass().getName());
                        }
                        ViewContract overlayContract = instantiatePlacement(overlayPlacement, overlayContext);
                        if (overlayContract != null) {
                            overlayContracts.put(overlayClass, overlayContract);
                        }
                    }
                }
            }

            return Scene.of(contract, module, overlayContracts);

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

    /**
     * Instantiate a contract from its ViewPlacement.
     */
    private ViewContract instantiatePlacement(ViewPlacement placement, ComponentContext context) {
        Lookup lookup = createLookup(context);
        ViewContract contract = placement.contractFactory().apply(lookup);
        if (contract != null) {
            contract.registerHandlers();
        }
        return contract;
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
