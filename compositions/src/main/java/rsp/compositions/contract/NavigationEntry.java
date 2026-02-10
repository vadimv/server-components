package rsp.compositions.contract;

import rsp.compositions.composition.Composition;
import rsp.compositions.composition.ContractMetadata;
import rsp.compositions.composition.Slot;
import rsp.compositions.composition.ViewPlacement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Framework-computed navigation metadata for PRIMARY contracts.
 * <p>
 * Each entry represents a unique navigable category with its
 * route and display label. Computed at app startup from composition registrations.
 * <p>
 * Used by navigation/explorer UI components to render menus without
 * needing to access framework internals directly.
 *
 * @param categoryKey the category key used for active highlighting
 * @param label     display label for navigation
 * @param contractClass the contract class (for SET_PRIMARY events)
 * @param route     the route pattern (e.g., "/posts")
 */
public record NavigationEntry(String categoryKey,
                              String label,
                              Class<? extends ViewContract> contractClass,
                              String route) {

    /**
     * Compute navigation entries from all compositions.
     * <p>
     * Iterates PRIMARY placements across all compositions, resolves metadata from
     * explicit categories, deduplicates by category key, and resolves routes from each
     * composition's router.
     *
     * @param compositions the list of all app compositions
     * @return immutable list of unique navigation entries
     */
    public static List<NavigationEntry> fromCompositions(List<Composition> compositions) {
        if (compositions == null) {
            return List.of();
        }

        final Map<String, NavigationEntry> uniqueByCategory = new LinkedHashMap<>();

        for (Composition comp : compositions) {
            for (ViewPlacement placement : comp.placementsForSlot(Slot.PRIMARY)) {
                ContractMetadata metadata = comp.metadataFor(placement.contractClass());
                String categoryKey = metadata.categoryKey();

                if (!uniqueByCategory.containsKey(categoryKey)) {
                    String route = comp.router()
                            .findRoutePattern(placement.contractClass())
                            .orElse("/");
                    String label = metadata.navigationLabel();
                    uniqueByCategory.put(categoryKey,
                            new NavigationEntry(categoryKey, label, placement.contractClass(), route));
                }
            }
        }

        return List.copyOf(uniqueByCategory.values());
    }
}
