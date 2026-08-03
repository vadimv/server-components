package rsp.compositions.contract;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.component.ComponentEventEntry;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.ContextKey;
import rsp.component.Lookup;
import rsp.component.Subscriber;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.routing.Router;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneContextEnricherTests {
    private static final ContextKey.StringKey<String> SOURCE_KEY =
            new ContextKey.StringKey<>("scene.enricher.source", String.class);
    private static final ContextKey.StringKey<String> OBSERVED_KEY =
            new ContextKey.StringKey<>("scene.enricher.observed", String.class);

    private final Subscriber subscriber = new NoOpSubscriber();

    @Test
    void scene_enrichment_does_not_construct_or_enrich_a_contract() {
        LookupReadingContract.instances.set(0);
        final Scene scene = Scene.of(
                ContractDescriptor.forContract(LookupReadingContract.class, Map.of()),
                Map.of(), composition());

        final ComponentContext enriched = new SceneContextEnricher("/lookup")
                .enrich(context("fresh"), scene);

        assertEquals(scene, enriched.get(ContextKeys.SCENE));
        assertEquals(0, LookupReadingContract.instances.get());
    }

    private Composition composition() {
        final Group group = new Group()
                .bind(LookupReadingContract.class, LookupReadingContract::new);
        return new Composition(
                new Router().route("/lookup", LookupReadingContract.class),
                new DefaultLayout(),
                group);
    }

    private ComponentContext context(String value) {
        return new ComponentContext()
                .with(Subscriber.class, subscriber)
                .with(SOURCE_KEY, value);
    }

    static final class LookupReadingContract extends ContractNodeComponent<String, Object> {
        static final AtomicInteger instances = new AtomicInteger();

        LookupReadingContract() {
            instances.incrementAndGet();
        }

        @Override
        public ComponentStateSupplier<String> initStateSupplier() {
            return (_, _) -> "ready";
        }

        @Override
        public ComponentView<String, Object> componentView() {
            return _ -> _ -> null;
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
