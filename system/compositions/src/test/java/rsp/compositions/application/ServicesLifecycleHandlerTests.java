package rsp.compositions.application;

import org.junit.jupiter.api.Test;
import rsp.component.Lookup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServicesLifecycleHandlerTests {

    static class TrackingService implements ServicesLifecycleHandler {
        final List<String> calls = new ArrayList<>();
        Lookup startLookup;

        @Override
        public void onStart(Lookup lookup) {
            calls.add("start");
            startLookup = lookup;
        }

        @Override
        public void onStop() {
            calls.add("stop");
        }
    }

    static class PlainService {
        // Does not implement ServicesLifecycleHandler
    }

    @Test
    void onStart_called_for_lifecycle_handler_service() {
        TrackingService service = new TrackingService();
        ServiceMapLookup lookup = new ServiceMapLookup(Map.of(TrackingService.class, service));

        service.onStart(lookup);

        assertEquals(List.of("start"), service.calls);
        assertSame(lookup, service.startLookup);
    }

    @Test
    void onStop_called_for_lifecycle_handler_service() {
        TrackingService service = new TrackingService();

        service.onStop();

        assertEquals(List.of("stop"), service.calls);
    }

    @Test
    void instanceof_check_filters_non_lifecycle_services() {
        PlainService plain = new PlainService();
        TrackingService tracking = new TrackingService();
        Map<Class<?>, Object> services = Map.of(
            PlainService.class, plain,
            TrackingService.class, tracking
        );

        // Simulate framework iteration
        for (Object service : services.values()) {
            if (service instanceof ServicesLifecycleHandler handler) {
                handler.onStart(new ServiceMapLookup(services));
            }
        }

        assertEquals(List.of("start"), tracking.calls);
    }

    @Test
    void serviceMapLookup_provides_service_by_class() {
        TrackingService service = new TrackingService();
        ServiceMapLookup lookup = new ServiceMapLookup(Map.of(TrackingService.class, service));

        assertSame(service, lookup.get(TrackingService.class));
    }

    @Test
    void serviceMapLookup_returns_null_for_unknown_class() {
        ServiceMapLookup lookup = new ServiceMapLookup(Map.of());

        assertNull(lookup.get(TrackingService.class));
    }

    @Test
    void serviceMapLookup_getRequired_throws_for_missing_service() {
        ServiceMapLookup lookup = new ServiceMapLookup(Map.of());

        assertThrows(IllegalStateException.class, () -> lookup.getRequired(TrackingService.class));
    }

    @Test
    void default_methods_are_no_ops() {
        // A service that only overrides onStart should not fail on onStop
        ServicesLifecycleHandler startOnly = new ServicesLifecycleHandler() {
            @Override
            public void onStart(Lookup lookup) {}
        };

        assertDoesNotThrow(startOnly::onStop);
    }

    @Test
    void composition_services_registration() {
        TrackingService service = new TrackingService();
        Services services = new Services()
            .service(TrackingService.class, service);

        Map<Class<?>, Object> map = services.asMap();
        assertEquals(1, map.size());
        assertSame(service, map.get(TrackingService.class));
    }
}
