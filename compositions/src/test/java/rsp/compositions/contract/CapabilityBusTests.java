package rsp.compositions.contract;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.application.TestLookup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class CapabilityBusTests {

    static final EventKey.SimpleKey<String> SEARCHABLE =
            new EventKey.SimpleKey<>("capability.searchable", String.class);

    static final EventKey.SimpleKey<Boolean> BULK_ACTIONS =
            new EventKey.SimpleKey<>("capability.bulkActions", Boolean.class);

    @Nested
    class ResolveTests {

        @Test
        void resolve_delivers_published_value_to_subscriber() {
            final CapabilityBus bus = new CapabilityBus();
            final AtomicReference<String> received = new AtomicReference<>();

            bus.publish(SEARCHABLE, "full-text");
            bus.subscribe(SEARCHABLE, received::set);
            bus.resolve();

            assertEquals("full-text", received.get());
        }

        @Test
        void resolve_delivers_when_subscribe_before_publish() {
            final CapabilityBus bus = new CapabilityBus();
            final AtomicReference<String> received = new AtomicReference<>();

            bus.subscribe(SEARCHABLE, received::set);
            bus.publish(SEARCHABLE, "full-text");
            bus.resolve();

            assertEquals("full-text", received.get());
        }

        @Test
        void resolve_delivers_to_multiple_subscribers() {
            final CapabilityBus bus = new CapabilityBus();
            final List<String> received = new ArrayList<>();

            bus.subscribe(SEARCHABLE, received::add);
            bus.subscribe(SEARCHABLE, received::add);
            bus.publish(SEARCHABLE, "config");
            bus.resolve();

            assertEquals(List.of("config", "config"), received);
        }

        @Test
        void resolve_delivers_multiple_publications_to_subscriber() {
            final CapabilityBus bus = new CapabilityBus();
            final List<String> received = new ArrayList<>();

            bus.publish(SEARCHABLE, "first");
            bus.publish(SEARCHABLE, "second");
            bus.subscribe(SEARCHABLE, received::add);
            bus.resolve();

            assertEquals(List.of("first", "second"), received);
        }

        @Test
        void resolve_does_not_cross_deliver_different_keys() {
            final CapabilityBus bus = new CapabilityBus();
            final AtomicReference<String> searchReceived = new AtomicReference<>();
            final AtomicBoolean bulkReceived = new AtomicBoolean();

            bus.publish(SEARCHABLE, "config");
            bus.subscribe(SEARCHABLE, searchReceived::set);
            bus.subscribe(BULK_ACTIONS, bulkReceived::set);
            bus.resolve();

            assertEquals("config", searchReceived.get());
            assertFalse(bulkReceived.get());
        }

        @Test
        void resolve_with_no_publications_does_nothing() {
            final CapabilityBus bus = new CapabilityBus();
            final AtomicReference<String> received = new AtomicReference<>();

            bus.subscribe(SEARCHABLE, received::set);
            bus.resolve();

            assertNull(received.get());
        }

        @Test
        void resolve_with_no_subscribers_does_nothing() {
            final CapabilityBus bus = new CapabilityBus();

            bus.publish(SEARCHABLE, "config");

            assertDoesNotThrow(bus::resolve);
        }

        @Test
        void resolve_with_empty_bus_does_nothing() {
            final CapabilityBus bus = new CapabilityBus();
            assertDoesNotThrow(bus::resolve);
        }
    }

    @Nested
    class PostResolveTests {

        @Test
        void publish_after_resolve_throws() {
            final CapabilityBus bus = new CapabilityBus();
            bus.resolve();

            assertThrows(IllegalStateException.class, () -> bus.publish(SEARCHABLE, "late"));
        }

        @Test
        void subscribe_after_resolve_throws() {
            final CapabilityBus bus = new CapabilityBus();
            bus.resolve();

            assertThrows(IllegalStateException.class, () -> bus.subscribe(SEARCHABLE, v -> {}));
        }
    }

    @Nested
    class ViewContractIntegrationTests {

        @Test
        void contract_publishes_and_sibling_receives_capability() {
            final CapabilityBus bus = new CapabilityBus();
            final TestLookup lookup = new TestLookup()
                    .withData(CapabilityBus.class, bus);

            // Provider contract publishes capability
            new ProviderContract(lookup);

            // Consumer contract subscribes to capability
            final ConsumerContract consumer = new ConsumerContract(lookup);

            // Resolve before rendering
            bus.resolve();

            assertTrue(consumer.searchEnabled);
            assertEquals("full-text", consumer.searchConfig);
        }

        @Test
        void contract_helpers_are_silent_when_no_bus_in_context() {
            final TestLookup lookup = new TestLookup();

            // Should not throw even without CapabilityBus in context
            assertDoesNotThrow(() -> new ProviderContract(lookup));
            assertDoesNotThrow(() -> new ConsumerContract(lookup));
        }

        static class ProviderContract extends ViewContract {
            ProviderContract(Lookup lookup) {
                super(lookup);
                publishCapability(SEARCHABLE, "full-text");
            }

            @Override
            public rsp.component.ComponentContext enrichContext(rsp.component.ComponentContext context) {
                return context;
            }

            @Override
            public String title() { return "Provider"; }
        }

        static class ConsumerContract extends ViewContract {
            boolean searchEnabled;
            String searchConfig;

            ConsumerContract(Lookup lookup) {
                super(lookup);
                onCapability(SEARCHABLE, config -> {
                    this.searchEnabled = true;
                    this.searchConfig = config;
                });
            }

            @Override
            public rsp.component.ComponentContext enrichContext(rsp.component.ComponentContext context) {
                return context;
            }

            @Override
            public String title() { return "Consumer"; }
        }
    }
}
