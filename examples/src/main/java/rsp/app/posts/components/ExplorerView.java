package rsp.app.posts.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.contract.EventKeys;
import rsp.dom.TreePositionPath;
import rsp.dsl.Definition;
import rsp.page.QualifiedSessionId;

import java.util.List;
import java.util.Objects;

import static rsp.dsl.Html.*;

/**
 * ExplorerView - Renders the Explorer navigation menu.
 * <p>
 * Reads EXPLORER_ITEMS and EXPLORER_ACTIVE_HINT from context (set by ExplorerContract)
 * and renders a navigation menu with SPA-style navigation.
 * <p>
 * Register in UiRegistry:
 * <pre>{@code
 * uiRegistry.register(ExplorerContract.class, ExplorerView::new)
 * }</pre>
 */
public class ExplorerView extends Component<ExplorerView.ExplorerViewState> {

    private static final Logger log = LoggerFactory.getLogger(ExplorerView.class);
    private Lookup lookup;

    public record ExplorerViewState(
            List<ExplorerItem> items,
            Object activeHint
    ) {}

    @Override
    public ComponentStateSupplier<ExplorerViewState> initStateSupplier() {
        return (_, context) -> {
            List<ExplorerItem> items = context.get(ExplorerContextKeys.EXPLORER_ITEMS);
            Object activeHint = context.get(ExplorerContextKeys.EXPLORER_ACTIVE_HINT);
            return new ExplorerViewState(
                    items != null ? items : List.of(),
                    activeHint
            );
        };
    }

    @Override
    public ComponentSegment<ExplorerViewState> createComponentSegment(final QualifiedSessionId sessionId,
                                                                       final TreePositionPath componentPath,
                                                                       final TreeBuilderFactory treeBuilderFactory,
                                                                       final ComponentContext componentContext,
                                                                       final CommandsEnqueue commandsEnqueue) {
        this.lookup = createLookup(componentContext, commandsEnqueue);
        return super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
    }

    private Lookup createLookup(ComponentContext context, CommandsEnqueue commandsEnqueue) {
        Subscriber subscriber = context.get(Subscriber.class);
        if (subscriber == null) {
            subscriber = NoOpSubscriber.INSTANCE;
        }
        return new ContextLookup(context, commandsEnqueue, subscriber);
    }

    @Override
    public ComponentView<ExplorerViewState> componentView() {
        return _ -> state -> div(attr("class", "explorer-panel"),
                div(attr("class", "explorer-header"), text("Explorer")),
                ul(attr("class", "explorer-menu"),
                        of(state.items().stream().map(item ->
                                renderMenuItem(item, state.activeHint())
                        ))
                )
        );
    }

    private Definition renderMenuItem(ExplorerItem item, Object activeHint) {
        boolean isActive = Objects.equals(item.typeHint(), activeHint);
        String cssClass = isActive ? "explorer-item active" : "explorer-item";
        String label = isActive ? item.label() + "*" : item.label();

        return li(attr("class", cssClass),
                a(
                        attr("href", item.route()),  // Real URL for copy/share
                        on("click", true, ctx -> {   // true = preventDefault
                            lookup.publish(ExplorerContract.REQUEST_OPEN_CONTRACT, item);
                        }),
                        text(label)
                )
        );
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
