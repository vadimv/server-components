package rsp.app.posts.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.NavigationEntry;
import rsp.compositions.contract.NavigationNode;
import rsp.dom.TreePositionPath;
import rsp.dsl.Definition;
import rsp.page.QualifiedSessionId;

import java.util.List;
import java.util.Objects;

import static rsp.dsl.Html.*;

/**
 * ExplorerView - Renders the Explorer navigation menu as a tree.
 * <p>
 * Reads a {@link NavigationNode} tree and the active category key from context
 * and renders nested groups with SPA-style navigation on routable leaves.
 * <p>
 * Register in Contracts:
 * <pre>{@code
 * contracts.register(ExplorerContract.class, ExplorerView::new)
 * }</pre>
 */
public class ExplorerView extends Component<ExplorerView.ExplorerViewState> {

    private static final Logger log = LoggerFactory.getLogger(ExplorerView.class);
    private Lookup lookup;
    private Lookup.Registration activeCategorySubscription;

    public record ExplorerViewState(
            NavigationNode tree,
            String activeCategoryKey
    ) {}

    @Override
    public ComponentStateSupplier<ExplorerViewState> initStateSupplier() {
        return (_, context) -> {
            NavigationNode tree = context.get(ContextKeys.NAVIGATION_TREE);
            String activeCategory = context.get(ContextKeys.PRIMARY_CATEGORY_KEY);
            return new ExplorerViewState(tree, activeCategory);
        };
    }

    @Override
    public ComponentSegment<ExplorerViewState> createComponentSegment(final QualifiedSessionId sessionId,
                                                                       final TreePositionPath componentPath,
                                                                       final TreeBuilderFactory treeBuilderFactory,
                                                                       final ComponentContext componentContext,
                                                                       final CommandsEnqueue commandsEnqueue) {
        final ComponentSegment<ExplorerViewState> segment = super.createComponentSegment(
                sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
        this.lookup = createLookup(segment, commandsEnqueue);
        return segment;
    }

    private Lookup createLookup(ComponentSegment<?> segment, CommandsEnqueue commandsEnqueue) {
        Subscriber subscriber = segment.componentContext().get(Subscriber.class);
        if (subscriber == null) {
            subscriber = NoOpSubscriber.INSTANCE;
        }
        return new ContextLookup(segment.contextScope(), commandsEnqueue, subscriber);
    }

    @Override
    public void onMounted(ComponentCompositeKey componentId,
                          ExplorerViewState state,
                          StateUpdate<ExplorerViewState> stateUpdate) {
        activeCategorySubscription = lookup.watch(ContextKeys.PRIMARY_CATEGORY_KEY, category ->
                stateUpdate.applyStateTransformation(current ->
                        new ExplorerViewState(current.tree(), category)));
    }

    @Override
    public void onUnmounted(ComponentCompositeKey componentId, ExplorerViewState state) {
        if (activeCategorySubscription != null) {
            activeCategorySubscription.unsubscribe();
            activeCategorySubscription = null;
        }
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    @Override
    public ComponentView<ExplorerViewState> componentView() {
        return _ -> state -> {
            NavigationNode root = state.tree();
            List<NavigationNode> topLevel = root == null
                    ? List.of()
                    : (root.label() == null ? root.children() : List.of(root));
            return div(attr("class", "explorer-panel"),
                    div(attr("class", "explorer-header"), text("Explorer")),
                    ul(attr("class", "explorer-menu"),
                            of(topLevel.stream().map(node ->
                                    renderNode(node, state.activeCategoryKey())
                            ))
                    )
            );
        };
    }

    private Definition renderNode(NavigationNode node, String activeCategoryKey) {
        NavigationEntry entry = node.entry();
        boolean isActive = entry != null && Objects.equals(entry.categoryKey(), activeCategoryKey);
        boolean hasChildren = !node.children().isEmpty();

        String cssClass = "explorer-item"
                + (entry == null ? " explorer-group" : "")
                + (isActive ? " active" : "");

        Definition labelPart = entry != null
                ? a(
                        attr("href", entry.route()),
                        on("click", true, ctx -> lookup.publish(ExplorerContract.REQUEST_OPEN_CONTRACT, entry)),
                        text(node.label())
                  )
                : div(attr("class", "explorer-group-label"), text(node.label()));

        Definition childrenPart = hasChildren
                ? ul(attr("class", "explorer-submenu"),
                        of(node.children().stream().map(c -> renderNode(c, activeCategoryKey)))
                  )
                : of();

        return li(attr("class", cssClass), labelPart, childrenPart);
    }

    /**
     * No-op Subscriber for components that only need to publish events.
     */
    private static final class NoOpSubscriber implements Subscriber {
        static final NoOpSubscriber INSTANCE = new NoOpSubscriber();

        @Override
        public void addWindowEventHandler(String eventType, java.util.function.Consumer<rsp.page.EventContext> eventHandler,
                                          boolean preventDefault, rsp.dom.DomEventEntry.Modifier modifier) {}

        @Override
        public void addComponentEventHandler(String eventType, java.util.function.Consumer<ComponentEventEntry.EventContext> eventHandler,
                                             boolean preventDefault) {}

        @Override
        public void removeComponentEventHandler(String eventType) {}
    }
}
