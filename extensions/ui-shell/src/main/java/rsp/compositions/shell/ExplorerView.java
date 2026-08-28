package rsp.compositions.shell;

import rsp.component.ComponentView;
import rsp.component.IntentDispatcher;
import rsp.compositions.block.NavigationEntry;
import rsp.compositions.block.NavigationNode;
import rsp.dsl.Definition;

import java.util.List;
import java.util.Objects;

import static rsp.dsl.Html.*;

/**
 * ExplorerView - Renders the Explorer navigation menu as a tree.
 * <p>
 * Renders the navigation tree and active category supplied by its block,
 * with SPA-style navigation on routable leaves.
 * <p>
 * Bind the block in a composition group:
 * <pre>{@code
 * group.bind(ExplorerBlock.class, () -> new ExplorerBlock(structure))
 * }</pre>
 */
public class ExplorerView implements ComponentView<ExplorerView.ExplorerViewState, ExplorerView.OpenBlock> {

    public record ExplorerViewState(
            NavigationNode tree,
            String activeCategoryKey
    ) {}

    public record OpenBlock(NavigationEntry entry) {}

    @Override
    public rsp.component.View<ExplorerViewState> resolve(IntentDispatcher<OpenBlock> intents) {
        return state -> {
            NavigationNode root = state.tree();
            List<NavigationNode> topLevel = root == null
                    ? List.of()
                    : (root.label() == null ? root.children() : List.of(root));
            return div(attr("class", "explorer-panel"),
                    div(attr("class", "explorer-header"), text("Explorer")),
                    ul(attr("class", "explorer-menu"),
                            of(topLevel.stream().map(node ->
                                    renderNode(node, state.activeCategoryKey(), intents)
                            ))
                    )
            );
        };
    }

    private Definition renderNode(NavigationNode node,
                                  String activeCategoryKey,
                                  IntentDispatcher<OpenBlock> intents) {
        NavigationEntry entry = node.entry();
        boolean isActive = entry != null && Objects.equals(entry.categoryKey(), activeCategoryKey);
        boolean hasChildren = !node.children().isEmpty();

        String cssClass = "explorer-item"
                + (entry == null ? " explorer-group" : "")
                + (isActive ? " active" : "");

        Definition labelPart = entry != null
                ? a(
                        attr("href", entry.route()),
                        on("click", true, ctx -> intents.dispatch(new OpenBlock(entry))),
                        text(node.label())
                  )
                : div(attr("class", "explorer-group-label"), text(node.label()));

        Definition childrenPart = hasChildren
                ? ul(attr("class", "explorer-submenu"),
                        of(node.children().stream().map(c -> renderNode(c, activeCategoryKey, intents)))
                  )
                : of();

        return li(attr("class", cssClass), labelPart, childrenPart);
    }

}
