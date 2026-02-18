package rsp.compositions.contract;

import rsp.compositions.composition.Composition;
import rsp.compositions.composition.ContractMetadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Framework-computed navigation metadata for routable contracts.
 * <p>
 * Each entry represents a unique navigable category with its
 * route and display label. Computed at app startup from composition registrations.
 * <p>
 * Only contracts with non-parameterized routes (no ":id" placeholders) are included,
 * since parameterized routes require context data and aren't top-level navigable.
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
     * Iterates all placements across all compositions, includes those with
     * non-parameterized routes, resolves metadata from explicit categories,
     * and deduplicates by category key.
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
            for (Class<? extends ViewContract> contractClass : comp.contracts().contractClasses()) {
                Optional<String> routeOpt = comp.router().findRoutePattern(contractClass);
                // Only include contracts with non-parameterized routes
                if (routeOpt.isPresent() && !routeOpt.get().contains(":")) {
                    ContractMetadata metadata = comp.metadataFor(contractClass);
                    String categoryKey = metadata.categoryKey();

                    if (!uniqueByCategory.containsKey(categoryKey)) {
                        String label = metadata.navigationLabel();
                        uniqueByCategory.put(categoryKey,
                                new NavigationEntry(categoryKey, label, contractClass, routeOpt.get()));
                    }
                }
            }
        }

        return List.copyOf(uniqueByCategory.values());
    }
}
