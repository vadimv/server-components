package rsp.app.posts.components;

import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.composition.Category;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.ContractMetadata;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.NavigationEntry;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.ViewContract;

import java.util.*;

import static rsp.compositions.contract.EventKeys.SET_PRIMARY;

/**
 * ExplorerContract - Navigation sidebar contract.
 * <p>
 * Builds navigation entries from compositions and categories,
 * and relays menu selection events as {@code SET_PRIMARY} commands.
 * <p>
 * Register in composition and configure as left sidebar in Layout:
 * <pre>{@code
 * contracts.bind(ExplorerContract.class, ctx -> new ExplorerContract(ctx, categories), ExplorerView::new)
 * new DefaultLayout().leftSidebar(ExplorerContract.class)
 * }</pre>
 */
public class ExplorerContract extends ViewContract {

    public static EventKey.SimpleKey<NavigationEntry> REQUEST_OPEN_CONTRACT =
            new EventKey.SimpleKey<>("explorer.open.contract", NavigationEntry.class);

    private final Category categories;
    private final List<NavigationEntry> entries;

    public ExplorerContract(Lookup lookup, Category categories) {
        super(lookup);
        this.categories = Objects.requireNonNull(categories);

        List<Composition> compositions = lookup.get(ContextKeys.APP_COMPOSITIONS);
        this.entries = buildNavigationEntries(compositions, categories);

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
            ContractMetadata metadata = categories.metadataFor(scene.routedContract().getClass());
            context = context.with(ContextKeys.PRIMARY_CATEGORY_KEY, metadata.categoryKey());
        }

        return context;
    }

    private static List<NavigationEntry> buildNavigationEntries(List<Composition> compositions, Category categories) {
        if (compositions == null) {
            return List.of();
        }

        final Map<String, NavigationEntry> uniqueByCategory = new LinkedHashMap<>();

        for (Composition comp : compositions) {
            for (Class<? extends ViewContract> contractClass : comp.contracts().contractClasses()) {
                Optional<String> routeOpt = comp.router().findRoutePattern(contractClass);
                if (routeOpt.isPresent() && !routeOpt.get().contains(":") && categories.contains(contractClass)) {
                    ContractMetadata metadata = categories.metadataFor(contractClass);
                    String categoryKey = metadata.categoryKey();

                    if (!uniqueByCategory.containsKey(categoryKey)) {
                        uniqueByCategory.put(categoryKey,
                                new NavigationEntry(categoryKey, metadata.navigationLabel(), contractClass, routeOpt.get()));
                    }
                }
            }
        }

        return List.copyOf(uniqueByCategory.values());
    }
}
