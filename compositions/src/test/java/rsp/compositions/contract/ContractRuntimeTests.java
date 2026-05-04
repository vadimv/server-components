package rsp.compositions.contract;

import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentEventEntry;
import rsp.component.ContextKey;
import rsp.component.Lookup;
import rsp.component.Subscriber;
import rsp.component.TreeBuilder;
import rsp.component.View;
import rsp.component.definitions.Component;
import rsp.component.definitions.StatelessComponent;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rsp.dsl.Html.div;

class ContractRuntimeTests {
    private static final ContextKey.StringKey<String> KEY =
            new ContextKey.StringKey<>("contract.runtime.key", String.class);

    private final CommandsEnqueue commands = _ -> {};
    private final Subscriber subscriber = new NoOpSubscriber();

    @Test
    void runtime_lookup_observes_replaced_context_until_destroyed() {
        final ContractRuntime runtime = ContractRuntime.instantiate(
                WatchingContract.class,
                WatchingContract::new,
                context("old"));
        final WatchingContract contract = (WatchingContract) runtime.contract();

        runtime.replaceContext(context("new"));

        assertEquals("new", contract.observed.get());
        assertEquals("new", runtime.contextScope().current().get(KEY));

        runtime.destroy();
        runtime.replaceContext(context("after-destroy"));

        assertEquals("new", contract.observed.get());
    }

    @Test
    void boundary_component_mirrors_final_context_without_enriching_contract() {
        final ContractRuntime runtime = ContractRuntime.instantiate(
                CountingContract.class,
                CountingContract::new,
                context("initial"));
        final ContractBoundaryComponent boundary = new ContractBoundaryComponent(
                runtime,
                new StatelessComponent((View<StatelessComponent.Unit>) _ -> div()));
        final ComponentContext boundaryContext = context("boundary");
        final TreeBuilder treeBuilder = new TreeBuilder(
                new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"),
                boundaryContext,
                commands);

        boundary.render(treeBuilder);

        assertEquals("boundary", runtime.contextScope().current().get(KEY));
        assertEquals(0, ((CountingContract) runtime.contract()).enrichCalls);
    }

    @Test
    void boundary_component_preserves_upstream_subscriber_for_child_view() {
        final ContractRuntime runtime = ContractRuntime.instantiate(
                CountingContract.class,
                CountingContract::new,
                context("initial"));
        final CapturingSubscriberComponent content = new CapturingSubscriberComponent();
        final ContractBoundaryComponent boundary = new ContractBoundaryComponent(runtime, content);
        final ComponentContext boundaryContext = context("boundary");
        final TreeBuilder treeBuilder = new TreeBuilder(
                new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"),
                boundaryContext,
                commands);

        boundary.render(treeBuilder);

        assertEquals(subscriber, content.observedSubscriber.get());
    }

    @Test
    void layer_context_preserves_show_data_for_runtime_mirroring() {
        final Map<String, Object> showData = Map.of("id", "42");
        final ContractRuntime runtime = ContractRuntime.instantiate(
                CountingContract.class,
                CountingContract::new,
                context("initial").with(ContextKeys.SHOW_DATA, showData));
        final LayerComponent layer = new LayerComponent((_, _, _) -> div());

        final ComponentContext enriched = layer.subComponentsContext().apply(
                context("layer"),
                new LayerComponent.LayerState(runtime, CountingContract.class, showData));
        runtime.replaceContext(enriched);

        assertEquals(showData, runtime.contextScope().current().get(ContextKeys.SHOW_DATA));
        assertEquals(Boolean.TRUE, runtime.contextScope().current().get(ContextKeys.IS_ACTIVE_CONTRACT));
        assertEquals(CountingContract.class, runtime.contextScope().current().get(ContextKeys.CONTRACT_CLASS));
    }

    private ComponentContext context(String value) {
        return new ComponentContext()
                .with(CommandsEnqueue.class, commands)
                .with(Subscriber.class, subscriber)
                .with(KEY, value);
    }

    static final class WatchingContract extends ViewContract {
        final AtomicReference<String> observed = new AtomicReference<>();

        WatchingContract(rsp.component.Lookup lookup) {
            super(lookup);
        }

        @Override
        protected void registerHandlers() {
            watch(KEY, observed::set);
        }

        @Override
        public ComponentContext enrichContext(ComponentContext context) {
            return context;
        }

        @Override
        public String title() {
            return "Watching";
        }
    }

    static final class CountingContract extends ViewContract {
        int enrichCalls;

        CountingContract(rsp.component.Lookup lookup) {
            super(lookup);
        }

        @Override
        public ComponentContext enrichContext(ComponentContext context) {
            enrichCalls++;
            return context;
        }

        @Override
        public String title() {
            return "Counting";
        }
    }

    static final class CapturingSubscriberComponent extends Component<Subscriber> {
        final AtomicReference<Subscriber> observedSubscriber = new AtomicReference<>();

        @Override
        public rsp.component.ComponentStateSupplier<Subscriber> initStateSupplier() {
            return (_, context) -> {
                Subscriber subscriber = context.get(Subscriber.class);
                observedSubscriber.set(subscriber);
                return subscriber;
            };
        }

        @Override
        public rsp.component.ComponentView<Subscriber> componentView() {
            return _ -> _ -> div();
        }
    }

    private static final class NoOpSubscriber implements Subscriber {
        @Override
        public void addWindowEventHandler(String eventType, java.util.function.Consumer<rsp.page.EventContext> eventHandler,
                                          boolean preventDefault, rsp.dom.DomEventEntry.Modifier modifier) {}

        @Override
        public Lookup.Registration addComponentEventHandler(String eventType,
                                                            java.util.function.Consumer<ComponentEventEntry.EventContext> eventHandler,
                                                            boolean preventDefault) {
            return () -> {};
        }
    }
}
