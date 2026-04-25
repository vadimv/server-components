package rsp.app.posts.components;

import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.NavigationEntry;
import rsp.compositions.contract.NavigationNode;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.ViewContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static rsp.compositions.contract.EventKeys.SET_PRIMARY;

/**
 * ExplorerContract - Navigation sidebar contract.
 * <p>
 * Builds a {@link NavigationNode} tree from compositions and a {@link StructureNode} tree,
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
    private final NavigationNode tree;

    public ExplorerContract(Lookup lookup, StructureNode structure) {
        super(lookup);
        this.structure = Objects.requireNonNull(structure);

        List<Composition> compositions = lookup.get(ContextKeys.APP_COMPOSITIONS);
        this.tree = buildNavigationTree(compositions, structure);

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
        context = context.with(ContextKeys.NAVIGATION_TREE, tree);

        Scene scene = context.get(ContextKeys.SCENE);
        if (scene != null && scene.routedContract() != null) {
            String categoryKey = structure.labelFor(scene.routedContract().getClass());
            if (categoryKey != null) {
                context = context.with(ContextKeys.PRIMARY_CATEGORY_KEY, categoryKey);
            }
        }

        return context;
    }

    private static NavigationNode buildNavigationTree(List<Composition> compositions,
                                                      StructureNode node) {
        List<NavigationNode> childNodes = new ArrayList<>();
        for (StructureNode child : node.children()) {
            NavigationNode childNode = buildNavigationTree(compositions, child);
            if (childNode != null) {
                childNodes.add(childNode);
            }
        }

        NavigationEntry entry = null;
        if (node.label() != null && compositions != null) {
            for (Class<? extends ViewContract> contractClass : node.contracts()) {
                Optional<String> routeOpt = findRoute(compositions, contractClass);
                if (routeOpt.isPresent() && !routeOpt.get().contains(":")) {
                    entry = new NavigationEntry(node.label(), node.label(), contractClass, routeOpt.get());
                    break;
                }
            }
        }

        if (node.label() == null && entry == null && childNodes.isEmpty()) {
            return null;
        }

        return new NavigationNode(node.label(), entry, List.copyOf(childNodes));
    }

    private static Optional<String> findRoute(List<Composition> compositions,
                                              Class<? extends ViewContract> contractClass) {
        for (Composition comp : compositions) {
            Optional<String> route = comp.router().findRoutePattern(contractClass);
            if (route.isPresent()) {
                return route;
            }
        }
        return Optional.empty();
    }
}
