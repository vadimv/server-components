package rsp.compositions.shell;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.EventKey;
import rsp.component.StateUpdater;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.block.Block;
import rsp.compositions.block.NavigationEntry;
import rsp.compositions.block.NavigationNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static rsp.compositions.block.EventKeys.SET_PRIMARY;

/**
 * ExplorerBlock - Navigation sidebar block.
 * <p>
 * Builds a {@link NavigationNode} tree from compositions and a {@link StructureNode} tree,
 * and relays menu selection events as {@code SET_PRIMARY} commands.
 * <p>
 * Register in composition and configure as left sidebar in Layout:
 * <pre>{@code
 * group.bind(ExplorerBlock.class, () -> new ExplorerBlock(mainBlocks.structureTree()))
 * new DefaultLayout().leftSidebar(ExplorerBlock.class)
 * }</pre>
 */
public class ExplorerBlock extends Block<ExplorerView.ExplorerViewState, ExplorerView.OpenBlock> {

    private final StructureNode structure;
    public ExplorerBlock(StructureNode structure) {
        this.structure = Objects.requireNonNull(structure);
    }

    @Override
    public String title() {
        return "Explorer";
    }

    @Override
    public ComponentStateSupplier<ExplorerView.ExplorerViewState> initStateSupplier() {
        return (_, context) -> {
            List<Composition> compositions = context.get(rsp.compositions.block.ContextKeys.APP_COMPOSITIONS);
            NavigationNode tree = buildNavigationTree(compositions, structure);
            String category = categoryFor(context);
            return new ExplorerView.ExplorerViewState(tree, category);
        };
    }

    @Override
    public ComponentView<ExplorerView.ExplorerViewState, ExplorerView.OpenBlock> componentView() {
        return new ExplorerView();
    }

    @Override
    protected void onBlockMounted(ExplorerView.ExplorerViewState state,
                                     StateUpdater<ExplorerView.ExplorerViewState> stateUpdate) {
        watch(rsp.compositions.block.ContextKeys.SCENE, (_, scene) ->
                stateUpdate.applyStateTransformation(current ->
                        new ExplorerView.ExplorerViewState(current.tree(), categoryFor(scene))));
    }

    @Override
    protected void onIntent(ExplorerView.OpenBlock intent,
                            ExplorerView.ExplorerViewState state,
                            StateUpdater<ExplorerView.ExplorerViewState> stateUpdater) {
        lookup().publish(SET_PRIMARY, intent.entry().blockClass());
    }

    private String categoryFor(rsp.component.ComponentContext context) {
        return categoryFor(context.get(rsp.compositions.block.ContextKeys.SCENE));
    }

    private String categoryFor(rsp.compositions.block.Scene scene) {
        return scene == null || scene.routedDescriptor() == null
                ? null
                : structure.labelFor(scene.routedDescriptor().blockClass());
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
            for (Class<? extends Block<?, ?>> blockClass : node.blocks()) {
                Optional<String> routeOpt = findRoute(compositions, blockClass);
                if (routeOpt.isPresent() && !routeOpt.get().contains(":")) {
                    entry = new NavigationEntry(node.label(), node.label(), blockClass, routeOpt.get());
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
                                              Class<? extends Block<?, ?>> blockClass) {
        for (Composition comp : compositions) {
            Optional<String> route = comp.router().findRoutePattern(blockClass);
            if (route.isPresent()) {
                return route;
            }
        }
        return Optional.empty();
    }
}
