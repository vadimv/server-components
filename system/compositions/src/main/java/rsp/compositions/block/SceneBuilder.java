package rsp.compositions.block;

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
 *   <li>The routed block becomes a descriptor</li>
 *   <li>Blocks required by the Layout become companion descriptors</li>
 *   <li>Live block instances are created only by DirectBlockHost on mount</li>
 * </ul>
 * <p>
 * When the routed block has a parent route (e.g., "/posts/:id" has parent "/posts"),
 * it is treated as an overlay-like block: the parent becomes the routed descriptor
 * and this block is pre-activated for LayerComponent auto-open.
 * <p>
 * Throws {@link IllegalStateException} if a required binding is missing.
 */
public final class SceneBuilder {

    private final Composition composition;
    private final BlockTarget target;
    private final String routePattern;
    private final Layout layout;

    public SceneBuilder(Composition composition,
                        Class<? extends Block<?, ?>> blockClass,
                        String routePattern,
                        Layout layout) {
        this(composition, composition.blocks().target(blockClass), routePattern, layout);
    }

    public SceneBuilder(Composition composition,
                        BlockTarget target,
                        String routePattern,
                        Layout layout) {
        this.composition = Objects.requireNonNull(composition, "composition");
        BlockTarget requestedTarget = Objects.requireNonNull(target, "target");
        BlockTarget configuredTarget = composition.blocks().target(requestedTarget.key());
        if (!configuredTarget.blockClass().equals(requestedTarget.blockClass())) {
            throw new IllegalArgumentException("Block key " + requestedTarget.key()
                    + " is bound to " + configuredTarget.blockClass().getName()
                    + ", not " + requestedTarget.blockClass().getName());
        }
        this.target = configuredTarget;
        this.routePattern = Objects.requireNonNull(routePattern, "routePattern");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /**
     * Build a complete Scene from the given context.
     *
     * @throws IllegalStateException if a required binding is missing
     */
    public Scene buildScene(ComponentContext context) {
        Group blocks = composition.blocks();

        // Verify block is registered
        if (!blocks.hasBinding(target.key())) {
            throw new IllegalStateException("Block not found in composition: " + target.key());
        }

        // Check if this block has a parent route → potentially overlay-like.
        // The layout's placement decision determines whether we auto-open over the parent
        // (modal) or route directly to the child as the primary (inline).
        Optional<Router.RouteMatch> parentRoute = composition.router().findParentRoute(routePattern);

        Scene scene;
        if (parentRoute.isPresent() && resolvesToModal(target)) {
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
                        parentRoute.get().blockKey(),
                        blocks.target(parentRoute.get().blockKey()).blockClass(),
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
     * Build scene for standard primary block (no parent route).
     */
    private Scene buildStandardScene() {
        BlockDescriptor routedDescriptor = BlockDescriptor.forTarget(target, Map.of());

        Map<Object, BlockDescriptor> companionDescriptors = describeCompanions();

        return Scene.of(routedDescriptor, companionDescriptors, composition);
    }

    /**
     * Build scene for overlay-like block routed directly via URL.
     * The parent block becomes the routed block; this block is pre-activated for LayerComponent.
     */
    private Scene buildAutoOpenScene(Router.RouteMatch parentRoute) {
        Group blocks = composition.blocks();

        if (!blocks.hasBinding(target.key())) {
            throw new IllegalStateException("Overlay block not found: " + target.key());
        }

        // Select the parent block as the routed descriptor
        Object parentKey = parentRoute.blockKey();
        if (!blocks.hasBinding(parentKey)) {
            throw new IllegalStateException(
                    "Parent block not found in composition: " + parentKey);
        }
        BlockTarget parentTarget = blocks.target(parentKey);

        BlockDescriptor parentDescriptor = BlockDescriptor.forTarget(parentTarget, Map.of());

        Map<Object, BlockDescriptor> companionDescriptors = describeCompanions();

        // The live overlay runtime is created by LayerComponent.
        BlockDescriptor overlayDescriptor = BlockDescriptor.forTarget(target, Map.of());
        Map<Object, BlockDescriptor> preActivated = new LinkedHashMap<>();
        preActivated.put(target.key(), overlayDescriptor);

        return Scene.withAutoOpen(parentDescriptor, companionDescriptors, preActivated, composition,
                new Scene.AutoOpen(target.key(), target.blockClass(), routePattern));
    }

    /**
     * Describe companion blocks declared by the Layout.
     */
    private Map<Object, BlockDescriptor> describeCompanions() {
        Set<Object> requiredByLayout = layout.requiredBlockKeys();
        Group blocks = composition.blocks();
        Map<Object, BlockDescriptor> companions = new LinkedHashMap<>();

        for (Object key : requiredByLayout) {
            if (blocks.hasBinding(key)) {
                BlockTarget companion = blocks.target(key);
                companions.put(companion.key(), BlockDescriptor.forTarget(companion, Map.of()));
            }
        }

        return companions;
    }

    /**
     * Whether the layout would render this block as a modal layer.
     * <p>
     * The Scene argument is null because no Scene exists at build time — the
     * resolver tolerates null and treats this as a "no routed descriptor yet" hint
     * (the first-in-* policies return INLINE in that case).
     */
    private boolean resolvesToModal(BlockTarget blockTarget) {
        PlacementDecision decision = layout.resolvePlacement(blockTarget, null);
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
