package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.compositions.composition.Composition;
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
 * Enrichments include: Scene reference, primary contract data and typeHint,
 * left sidebar data, active contracts by slot, overlay contract data with title
 * preservation, and edit slot/route info.
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
        if (scene == null || !scene.isValid()) {
            return context; // Render will show error
        }

        ViewContract contract = scene.primaryContract();
        Composition composition = scene.composition();

        // Add Scene to context so contracts can use SlotUtils
        ComponentContext enrichedContext = context.with(ContextKeys.SCENE, scene);

        // Let the primary contract enrich context with its data (items, schema, etc.)
        enrichedContext = contract.enrichContext(enrichedContext);

        // Add primary contract's typeHint to context for Explorer highlighting
        // This is dynamic - it updates when the primary contract changes via SET_PRIMARY
        Object primaryTypeHint = contract.typeHint();
        if (primaryTypeHint != null) {
            enrichedContext = enrichedContext.with(ContextKeys.PRIMARY_TYPE_HINT, primaryTypeHint);
        }

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

        // Add non-primary contracts to context if present (backward compatibility)
        if (scene.hasNonPrimaryContracts()) {
            Map<Class<? extends ViewContract>, ViewContract> nonPrimary = scene.nonPrimaryContracts();
            enrichedContext = enrichedContext.with(ContextKeys.OVERLAY_CONTRACTS, nonPrimary);

            // Preserve primary contract's title before overlay enrichment
            // (overlay contracts may set their own CONTRACT_TITLE which would overwrite it)
            String primaryTitle = enrichedContext.get(ContextKeys.CONTRACT_TITLE);

            // Enrich context with each non-primary contract's data
            for (ViewContract nonPrimaryContract : nonPrimary.values()) {
                enrichedContext = nonPrimaryContract.enrichContext(enrichedContext);
                // Store the contract instance for event handling
                enrichedContext = enrichedContext.with(
                        ContextKeys.OVERLAY_VIEW_CONTRACT.with(nonPrimaryContract.getClass().getName()),
                        nonPrimaryContract);
            }

            // Capture overlay title (set by overlay contracts during enrichment)
            // and store it in OVERLAY_TITLE for EditView to use
            String overlayTitle = enrichedContext.get(ContextKeys.CONTRACT_TITLE);
            if (overlayTitle != null && !overlayTitle.equals(primaryTitle)) {
                enrichedContext = enrichedContext.with(ContextKeys.OVERLAY_TITLE, overlayTitle);
            }

            // Restore primary contract's title (the list view should show "Posts", not "Edit Post")
            if (primaryTitle != null) {
                enrichedContext = enrichedContext.with(ContextKeys.CONTRACT_TITLE, primaryTitle);
            }
        }

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
