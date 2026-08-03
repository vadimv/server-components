package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.compositions.application.ServicesLifecycleHandler;
import rsp.compositions.application.Services;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.layout.Layout;
import rsp.compositions.layout.PlacementDecision;
import rsp.compositions.routing.Router;
import rsp.server.http.Fragment;
import rsp.server.http.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Constructs Scene instances from composition configuration.
 * <p>
 * Lifecycle derivation:
 * <ul>
 *   <li>The routed contract becomes a descriptor</li>
 *   <li>Contracts required by the Layout become companion descriptors</li>
 *   <li>Live contract instances are created only by DirectContractHost on mount</li>
 * </ul>
 * <p>
 * When the routed contract has a parent route (e.g., "/posts/:id" has parent "/posts"),
 * it is treated as an overlay-like contract: the parent becomes the routed descriptor
 * and this contract is pre-activated for LayerComponent auto-open.
 * <p>
 * Throws {@link IllegalStateException} if a required binding is missing.
 */
public final class SceneBuilder {

    private final Composition composition;
    private final Class<? extends Contract> contractClass;
    private final String routePattern;
    private final Layout layout;

    public SceneBuilder(Composition composition,
                        Class<? extends Contract> contractClass,
                        String routePattern,
                        Layout layout) {
        this.composition = Objects.requireNonNull(composition, "composition");
        this.contractClass = Objects.requireNonNull(contractClass, "contractClass");
        this.routePattern = Objects.requireNonNull(routePattern, "routePattern");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /**
     * Build a complete Scene from the given context.
     *
     * @throws IllegalStateException if a required binding is missing
     */
    public Scene buildScene(ComponentContext context) {
        Group contracts = composition.contracts();

        // Verify contract is registered
        if (!contracts.hasBinding(this.contractClass)) {
            throw new IllegalStateException("Contract not found in composition: " + this.contractClass.getName());
        }

        // Check if this contract has a parent route → potentially overlay-like.
        // The layout's placement decision determines whether we auto-open over the parent
        // (modal) or route directly to the child as the primary (inline).
        Optional<Router.RouteMatch> parentRoute = composition.router().findParentRoute(routePattern);

        Scene scene;
        if (parentRoute.isPresent() && resolvesToModal(this.contractClass)) {
            scene = buildAutoOpenScene(parentRoute.get());
        } else {
            scene = buildStandardScene();
            // Inline placement reached via direct URL hit on a child route
            // (e.g., refresh of /comments/3, or shared link): seed a return target
            // so save/cancel navigates back to the parent list, mirroring the
            // SHOW-driven inline flow. Without this, ACTION_SUCCESS would refresh
            // the form in place and Save/Cancel would appear to do nothing.
            if (parentRoute.isPresent()) {
                Scene.InlineReturnTarget rt = new Scene.InlineReturnTarget(
                        parentRoute.get().contractClass(),
                        parentRoute.get().pattern(),
                        captureQuery(context),
                        captureFragment(context));
                scene = scene.withInlineReturnTarget(rt);
            }
        }

        startServicesLifecycleHandlers(context);

        return scene;
    }

    private Query captureQuery(ComponentContext context) {
        String prefix = ContextKeys.URL_QUERY.baseKey() + ".";
        Map<String, Object> entries = context.stringEntriesWithPrefix(prefix);
        if (entries.isEmpty()) {
            return Query.EMPTY;
        }
        List<Query.Parameter> params = new ArrayList<>(entries.size());
        for (Map.Entry<String, Object> e : entries.entrySet()) {
            String name = e.getKey().substring(prefix.length());
            if (e.getValue() instanceof String value) {
                params.add(new Query.Parameter(name, value));
            }
        }
        return params.isEmpty() ? Query.EMPTY : new Query(params);
    }

    private Fragment captureFragment(ComponentContext context) {
        String value = context.get(ContextKeys.URL_FRAGMENT);
        return (value == null || value.isEmpty()) ? Fragment.EMPTY : new Fragment(value);
    }

    /**
     * Build scene for standard primary contract (no parent route).
     */
    private Scene buildStandardScene() {
        ContractDescriptor routedDescriptor = ContractDescriptor.forContract(this.contractClass, Map.of());

        Map<Class<? extends Contract>, ContractDescriptor> companionDescriptors = describeCompanions();

        return Scene.of(routedDescriptor, companionDescriptors, composition);
    }

    /**
     * Build scene for overlay-like contract routed directly via URL.
     * The parent contract becomes the routed contract; this contract is pre-activated for LayerComponent.
     */
    private Scene buildAutoOpenScene(Router.RouteMatch parentRoute) {
        Group contracts = composition.contracts();

        if (!contracts.hasBinding(this.contractClass)) {
            throw new IllegalStateException("Overlay contract not found: " + this.contractClass.getName());
        }

        // Select the parent contract as the routed descriptor
        Class<? extends Contract> parentClass = parentRoute.contractClass();
        if (!contracts.hasBinding(parentClass)) {
            throw new IllegalStateException(
                    "Parent contract not found in composition: " + parentClass.getName());
        }

        ContractDescriptor parentDescriptor = ContractDescriptor.forContract(parentClass, Map.of());

        Map<Class<? extends Contract>, ContractDescriptor> companionDescriptors = describeCompanions();

        // The live overlay runtime is created by LayerComponent.
        ContractDescriptor overlayDescriptor = ContractDescriptor.forContract(this.contractClass, Map.of());
        Map<Class<? extends Contract>, ContractDescriptor> preActivated = new LinkedHashMap<>();
        preActivated.put(this.contractClass, overlayDescriptor);

        return Scene.withAutoOpen(parentDescriptor, companionDescriptors, preActivated, composition,
                new Scene.AutoOpen(this.contractClass, routePattern));
    }

    /**
     * Describe companion contracts declared by the Layout.
     */
    private Map<Class<? extends Contract>, ContractDescriptor> describeCompanions() {
        Set<Class<? extends Contract>> requiredByLayout = layout.requiredContracts();
        Group contracts = composition.contracts();
        Map<Class<? extends Contract>, ContractDescriptor> companions = new LinkedHashMap<>();

        for (Class<? extends Contract> cls : contracts.contractClasses()) {
            if (requiredByLayout.contains(cls)) {
                if (contracts.hasBinding(cls)) {
                    companions.put(cls, ContractDescriptor.forContract(cls, Map.of()));
                }
            }
        }

        return companions;
    }

    /**
     * Whether the layout would render this contract as a modal layer.
     * <p>
     * The Scene argument is null because no Scene exists at build time — the
     * resolver tolerates null and treats this as a "no routed descriptor yet" hint
     * (the first-in-* policies return INLINE in that case).
     */
    private boolean resolvesToModal(Class<? extends Contract> contractClass) {
        PlacementDecision decision = layout.resolvePlacement(contractClass, null);
        return decision.placement().isModal();
    }

    private void startServicesLifecycleHandlers(ComponentContext context) {
        Services services = composition.services();
        if (services == null) return;
        Lookup lookup = LookupFactory.create(context);
        for (Object service : services.asMap().values()) {
            if (service instanceof ServicesLifecycleHandler handler) {
                handler.onStart(lookup);
            }
        }
    }
}
