package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.ContractMetadata;
import rsp.compositions.composition.Slot;
import rsp.compositions.composition.ViewPlacement;
import rsp.compositions.routing.Router;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Enriches ComponentContext with Scene-derived data for downstream UI components.
 * <p>
 * Pure data transformation: (context, scene) -> enriched context.
 * <p>
 * Enrichments include: Scene reference, primary contract data and category key,
 * sidebar data, active contracts by slot, and edit slot/route info.
 * Overlay/layer context enrichment is handled by LayerComponent.
 */
public final class SceneContextEnricher {

    private final String routePattern;

    public SceneContextEnricher(String routePattern) {
        this.routePattern = Objects.requireNonNull(routePattern, "routePattern");
    }

    /**
     * Enrich context with scene data for downstream components.
     */
    public ComponentContext enrich(ComponentContext context, Scene scene) {
        if (scene == null) {
            return context;
        }

        ViewContract contract = scene.primaryContract();
        Composition composition = scene.composition();

        // Add Scene to context so contracts can use SlotUtils
        ComponentContext enrichedContext = context.with(ContextKeys.SCENE, scene);

        // Let the primary contract enrich context with its data (items, schema, etc.)
        enrichedContext = contract.enrichContext(enrichedContext);

        // Resolve metadata from composition categories
        ContractMetadata primaryMeta = composition != null
                ? composition.metadataFor(contract.getClass())
                : new ContractMetadata("App", "App");

        // Add primary contract's category key to context for Explorer highlighting
        enrichedContext = enrichedContext.with(ContextKeys.PRIMARY_CATEGORY_KEY, primaryMeta.categoryKey());

        // Let the LEFT_SIDEBAR contract enrich context with its data (if present)
        ViewContract leftSidebarContract = scene.leftSidebarContract();
        if (leftSidebarContract != null) {
            enrichedContext = leftSidebarContract.enrichContext(enrichedContext);
        }

        // Let the RIGHT_SIDEBAR contract enrich context with its data (if present)
        ViewContract rightSidebarContract = scene.rightSidebarContract();
        if (rightSidebarContract != null) {
            enrichedContext = rightSidebarContract.enrichContext(enrichedContext);
        }

        // Add active contracts by slot to context (for Layout to read)
        enrichedContext = enrichedContext.with(
            ContextKeys.ACTIVE_CONTRACTS_BY_SLOT,
            scene.activeContractsBySlot().entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().stream().map(Scene.ActiveContract::contract).toList()
                ))
        );

        // Add edit slot/route info to context for DefaultListView (use composition's router)
        enrichedContext = enrichEditSlotInfo(enrichedContext, composition, composition.router());

        return enrichedContext;
    }

    /**
     * Add edit contract slot and route info to context.
     * This helps DefaultListView determine how to render the Edit button.
     */
    private ComponentContext enrichEditSlotInfo(ComponentContext context, Composition composition, Router router) {
        // Find EditViewContract-based placement in the composition
        ViewPlacement editPlacement = null;
        for (ViewPlacement placement : composition.views()) {
            if (EditViewContract.class.isAssignableFrom(placement.contractClass())) {
                editPlacement = placement;
                break;
            }
        }

        if (editPlacement == null) {
            return context; // No edit contract in this composition
        }

        Slot editSlot = editPlacement.slot();
        context = context.with(ContextKeys.EDIT_SLOT, editSlot);

        // Check if edit contract has a route
        boolean hasRoute = router != null && router.hasRoute(editPlacement.contractClass());
        context = context.with(ContextKeys.EDIT_HAS_ROUTE, hasRoute);

        // If it has a route, derive the edit pattern from the constructor-provided routePattern
        if (hasRoute && router != null) {
            // Assume edit pattern is list pattern + "/:id"
            String editPattern = this.routePattern + "/:id";
            context = context.with(ContextKeys.EDIT_ROUTE_PATTERN, editPattern);
        }

        return context;
    }
}
