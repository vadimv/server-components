package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.compositions.composition.Composition;
import rsp.compositions.routing.Router;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;

import java.util.Objects;
import java.util.Optional;

/**
 * Enriches ComponentContext with Scene-derived data for downstream UI components.
 * <p>
 * Pure data transformation: (context, scene) -> enriched context.
 * <p>
 * Enrichments include: scene navigation metadata, scene-local URL state, and
 * edit route info. Contract components enrich their own descendant context.
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
        context = applyEffectiveUrl(context, scene, composition);

        // Add Scene to context
        ComponentContext enrichedContext = context.with(ContextKeys.SCENE, scene);

        // Layers are siblings of the primary branch, so keep the primary title
        // available at scene scope. The active contract overrides it for its
        // descendants and restores it for overlay branches.
        enrichedContext = enrichedContext.with(ContextKeys.CONTRACT_TITLE, scene.pageTitle());

        // Add edit route info to context for DefaultListView
        enrichedContext = enrichEditInfo(enrichedContext, composition, composition.router());

        return enrichedContext;
    }

    /**
     * Apply scene-local URL state before downstream contracts read context.
     * <p>
     * PUSH_URL_ONLY transitions update browser history without changing the
     * root URL component state. The Scene records that effective URL so
     * contracts observe the same path/query/fragment the browser displays.
     */
    private ComponentContext applyEffectiveUrl(ComponentContext context,
                                               Scene scene,
                                               Composition composition) {
        RelativeUrl effectiveUrl = scene.effectiveUrl();
        if (effectiveUrl == null) {
            return context;
        }

        ComponentContext next = context
                .withoutStringPrefix(ContextKeys.URL_QUERY.baseKey() + ".")
                .withoutStringPrefix(ContextKeys.URL_PATH.baseKey() + ".");

        next = next
                .with(ContextKeys.URL_PATH_FULL, effectiveUrl.path())
                .with(ContextKeys.URL_FRAGMENT,
                        effectiveUrl.fragment() == null ? "" : effectiveUrl.fragment().fragmentString());

        for (int i = 0; i < effectiveUrl.path().elementsCount(); i++) {
            next = next.with(ContextKeys.URL_PATH.with(String.valueOf(i)), effectiveUrl.path().get(i));
        }

        for (Query.Parameter param : effectiveUrl.query().parameters()) {
            next = next.with(ContextKeys.URL_QUERY.with(param.name()), param.value());
        }

        if (scene.routedDescriptor() == null) {
            return next;
        }

        Class<? extends Contract> contractClass = scene.routedDescriptor().contractClass();
        next = next
                .with(ContextKeys.ROUTE_COMPOSITION, composition)
                .with(ContextKeys.ROUTE_CONTRACT_CLASS, contractClass)
                .with(ContextKeys.ROUTE_PATH, effectiveUrl.path().toString());

        if (composition.router() != null) {
            Optional<String> routePattern = composition.router().findRoutePattern(contractClass);
            if (routePattern.isPresent()) {
                next = next.with(ContextKeys.ROUTE_PATTERN, routePattern.get());
            }
        }

        return next;
    }

    /**
     * Add edit contract route info to context.
     * This helps DefaultListView determine how to render the Edit button.
     */
    private ComponentContext enrichEditInfo(ComponentContext context, Composition composition, Router router) {
        // Find the edit contract class in the composition.
        Class<? extends Contract> editContractClass = null;
        for (Class<? extends Contract> cls : composition.contracts().contractClasses()) {
            if (EditContractComponent.class.isAssignableFrom(cls)) {
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
