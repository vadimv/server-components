package rsp.compositions.shell;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.EventKey;
import rsp.component.StateUpdater;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ContractNodeComponent;
import rsp.compositions.contract.NavigationEntry;
import rsp.compositions.contract.NavigationNode;
import rsp.compositions.contract.Contract;

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
 * group.bind(ExplorerContract.class, () -> new ExplorerContract(mainContracts.structureTree()))
 * new DefaultLayout().leftSidebar(ExplorerContract.class)
 * }</pre>
 */
public class ExplorerContract extends ContractNodeComponent<ExplorerView.ExplorerViewState, ExplorerView.OpenContract> {

    private final StructureNode structure;
    public ExplorerContract(StructureNode structure) {
        this.structure = Objects.requireNonNull(structure);
    }

    @Override
    public String title() {
        return "Explorer";
    }

    @Override
    public ComponentStateSupplier<ExplorerView.ExplorerViewState> initStateSupplier() {
        return (_, context) -> {
            List<Composition> compositions = context.get(rsp.compositions.contract.ContextKeys.APP_COMPOSITIONS);
            NavigationNode tree = buildNavigationTree(compositions, structure);
            String category = categoryFor(context);
            return new ExplorerView.ExplorerViewState(tree, category);
        };
    }

    @Override
    public ComponentView<ExplorerView.ExplorerViewState, ExplorerView.OpenContract> componentView() {
        return new ExplorerView();
    }

    @Override
    protected void onContractMounted(ExplorerView.ExplorerViewState state,
                                     StateUpdater<ExplorerView.ExplorerViewState> stateUpdate) {
        watch(rsp.compositions.contract.ContextKeys.SCENE, (_, scene) ->
                stateUpdate.applyStateTransformation(current ->
                        new ExplorerView.ExplorerViewState(current.tree(), categoryFor(scene))));
    }

    @Override
    protected void onIntent(ExplorerView.OpenContract intent,
                            ExplorerView.ExplorerViewState state,
                            StateUpdater<ExplorerView.ExplorerViewState> stateUpdater) {
        lookup().publish(SET_PRIMARY, intent.entry().contractClass());
    }

    private String categoryFor(rsp.component.ComponentContext context) {
        return categoryFor(context.get(rsp.compositions.contract.ContextKeys.SCENE));
    }

    private String categoryFor(rsp.compositions.contract.Scene scene) {
        return scene == null || scene.routedDescriptor() == null
                ? null
                : structure.labelFor(scene.routedDescriptor().contractClass());
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
            for (Class<? extends Contract> contractClass : node.contracts()) {
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
                                              Class<? extends Contract> contractClass) {
        for (Composition comp : compositions) {
            Optional<String> route = comp.router().findRoutePattern(contractClass);
            if (route.isPresent()) {
                return route;
            }
        }
        return Optional.empty();
    }
}
