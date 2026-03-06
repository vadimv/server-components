package rsp.app.posts.components;

import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.NavigationEntry;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.ViewContract;

import java.util.*;

import static rsp.compositions.contract.EventKeys.SET_PRIMARY;

/**
 * ExplorerContract - Navigation sidebar contract.
 * <p>
 * Builds navigation entries from compositions and a {@link StructureNode} tree,
 * and relays menu selection events as {@code SET_PRIMARY} commands.
 * <p>
 * Register in composition and configure as left sidebar in Layout:
 * <pre>{@code
 * group.bind(ExplorerContract.class, ctx -> new ExplorerContract(ctx, mainContracts.structureTree()), ExplorerView::new)
 * new DefaultLayout().leftSidebar(ExplorerContract.class)
 * }</pre>
 */
public class ExplorerContract extends ViewContract {

    public static EventKey.SimpleKey<NavigationEntry> REQUEST_OPEN_CONTRACT =
            new EventKey.SimpleKey<>("explorer.open.contract", NavigationEntry.class);

    private final StructureNode structure;
    private final List<NavigationEntry> entries;

    public ExplorerContract(Lookup lookup, StructureNode structure) {
        super(lookup);
        this.structure = Objects.requireNonNull(structure);

        List<Composition> compositions = lookup.get(ContextKeys.APP_COMPOSITIONS);
        this.entries = buildNavigationEntries(compositions, structure);

        subscribe(REQUEST_OPEN_CONTRACT, (eventName, entry) -> {
            lookup.publish(SET_PRIMARY, entry.contractClass());
        });
    }

    @Override
    public String title() {
        return "Explorer";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        context = context.with(ContextKeys.NAVIGATION_ENTRIES, entries);

        Scene scene = context.get(ContextKeys.SCENE);
        if (scene != null && scene.routedContract() != null) {
            String categoryKey = structure.labelFor(scene.routedContract().getClass());
            if (categoryKey != null) {
                context = context.with(ContextKeys.PRIMARY_CATEGORY_KEY, categoryKey);
            }
        }

        return context;
    }

    private static List<NavigationEntry> buildNavigationEntries(List<Composition> compositions,
                                                                StructureNode structure) {
        if (compositions == null) {
            return List.of();
        }

        final Map<String, NavigationEntry> uniqueByCategory = new LinkedHashMap<>();

        for (Composition comp : compositions) {
            for (Class<? extends ViewContract> contractClass : comp.contracts().contractClasses()) {
                Optional<String> routeOpt = comp.router().findRoutePattern(contractClass);
                if (routeOpt.isPresent() && !routeOpt.get().contains(":") && structure.contains(contractClass)) {
                    String label = structure.labelFor(contractClass);
                    if (label != null && !uniqueByCategory.containsKey(label)) {
                        uniqueByCategory.put(label,
                                new NavigationEntry(label, label, contractClass, routeOpt.get()));
                    }
                }
            }
        }

        return List.copyOf(uniqueByCategory.values());
    }
}
