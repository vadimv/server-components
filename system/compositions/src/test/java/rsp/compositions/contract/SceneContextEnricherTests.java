package rsp.compositions.contract;

import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentEventEntry;
import rsp.component.ContextKey;
import rsp.component.Lookup;
import rsp.component.Subscriber;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.routing.Router;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneContextEnricherTests {
    private static final ContextKey.StringKey<String> SOURCE_KEY =
            new ContextKey.StringKey<>("scene.enricher.source", String.class);
    private static final ContextKey.StringKey<String> OBSERVED_KEY =
            new ContextKey.StringKey<>("scene.enricher.observed", String.class);

    private final CommandsEnqueue commands = _ -> {};
    private final Subscriber subscriber = new NoOpSubscriber();

    @Test
    void routed_contract_lookup_is_refreshed_before_enrich_context() {
        final ContractRuntime runtime = ContractRuntime.instantiate(
                LookupReadingContract.class,
                LookupReadingContract::new,
                context("old"));
        final Scene scene = Scene.of(runtime, Map.of(), Map.of(), composition());

        final ComponentContext enriched = new SceneContextEnricher("/lookup")
                .enrich(context("fresh"), scene);

        assertEquals("fresh", enriched.get(OBSERVED_KEY));
        assertEquals("fresh", runtime.contextScope().current().get(SOURCE_KEY));
    }

    private Composition composition() {
        final Group group = new Group()
                .bind(LookupReadingContract.class, LookupReadingContract::new, () -> null);
        return new Composition(
                new Router().route("/lookup", LookupReadingContract.class),
                new DefaultLayout(),
                group);
    }

    private ComponentContext context(String value) {
        return new ComponentContext()
                .with(CommandsEnqueue.class, commands)
                .with(Subscriber.class, subscriber)
                .with(SOURCE_KEY, value);
    }

    static final class LookupReadingContract extends ViewContract {
        LookupReadingContract(Lookup lookup) {
            super(lookup);
        }

        @Override
        public ComponentContext enrichContext(ComponentContext context) {
            return context.with(OBSERVED_KEY, lookup.get(SOURCE_KEY));
        }

        @Override
        public String title() {
            return "Lookup";
        }
    }

    private static final class NoOpSubscriber implements Subscriber {
        @Override
        public void addWindowEventHandler(String eventType,
                                          java.util.function.Consumer<rsp.page.EventContext> eventHandler,
                                          boolean preventDefault,
                                          rsp.dom.DomEventEntry.Modifier modifier) {}

        @Override
        public Lookup.Registration addComponentEventHandler(String eventType,
                                                            java.util.function.Consumer<ComponentEventEntry.EventContext> eventHandler,
                                                            boolean preventDefault) {
            return () -> {};
        }
    }
}
