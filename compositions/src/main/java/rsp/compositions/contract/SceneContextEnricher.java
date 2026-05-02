package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.compositions.composition.Composition;
import rsp.compositions.routing.Router;

import java.util.Objects;
import java.util.Optional;

/**
 * Enriches ComponentContext with Scene-derived data for downstream UI components.
 * <p>
 * Pure data transformation: (context, scene) -> enriched context.
 * <p>
 * Enrichments include: Scene reference, routed contract data,
 * companion contract data, and edit route info.
 * Overlay/layer context enrichment is handled by LayerComponent.
 */
public final class SceneContextEnricher {

    public SceneContextEnricher(String routePattern) {
        Objects.requireNonNull(routePattern, "routePattern");
    }

    /**
     * Enrich context with scene data for downstream components.
     */
    public ComponentContext enrich(ComponentContext context, Scene scene) {
        if (scene == null) {
            return context;
        }

        Composition composition = scene.composition();

        // Add Scene to context
        ComponentContext enrichedContext = context.with(ContextKeys.SCENE, scene);

        // Let the routed contract enrich context with its data (items, schema, etc.)
        if (scene.routedRuntime() != null) {
            scene.routedRuntime().replaceContext(enrichedContext);
            enrichedContext = scene.routedRuntime().contract().enrichContext(enrichedContext);
        }

        // Let all companion contracts enrich context with their data
        for (ContractRuntime companion : scene.companionRuntimes().values()) {
            companion.replaceContext(enrichedContext);
            enrichedContext = companion.contract().enrichContext(enrichedContext);
        }

        // Add edit route info to context for DefaultListView
        enrichedContext = enrichEditInfo(enrichedContext, composition, composition.router());

        return enrichedContext;
    }

    /**
     * Add edit contract route info to context.
     * This helps DefaultListView determine how to render the Edit button.
     */
    private ComponentContext enrichEditInfo(ComponentContext context, Composition composition, Router router) {
        // Find EditViewContract-based contract class in the composition
        Class<? extends ViewContract> editContractClass = null;
        for (Class<? extends ViewContract> cls : composition.contracts().contractClasses()) {
            if (EditViewContract.class.isAssignableFrom(cls)) {
                editContractClass = cls;
                break;
            }
        }

        if (editContractClass == null) {
            return context; // No edit contract in this composition
        }

        // Check if edit contract has a route
        boolean hasRoute = router != null && router.hasRoute(editContractClass);
        context = context.with(ContextKeys.EDIT_HAS_ROUTE, hasRoute);

        if (hasRoute && router != null) {
            Optional<String> editRouteOpt = router.findRoutePattern(editContractClass);
            if (editRouteOpt.isPresent()) {
                context = context.with(ContextKeys.EDIT_ROUTE_PATTERN, editRouteOpt.get());
                // Overlay-like if route has a parent (e.g., /posts/:id has parent /posts)
                boolean opensAsOverlay = router.findParentRoute(editRouteOpt.get()).isPresent();
                context = context.with(ContextKeys.EDIT_OPENS_AS_OVERLAY, opensAsOverlay);
            }
        }

        return context;
    }
}
