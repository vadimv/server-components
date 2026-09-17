package rsp.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationContextTests {
    @Test
    void validates_and_exposes_typed_immutable_services_and_config() {
        String service = "service";
        ApplicationConfig config = new ApplicationConfig().with("name", "test");
        ApplicationContext context = ApplicationContext.builder()
                .config(config)
                .service(String.class, service)
                .build();

        assertSame(config, context.config());
        assertSame(service, context.get(String.class));
        assertSame(service, context.require(String.class));
        assertThrows(IllegalStateException.class, () -> context.require(Integer.class));
        assertThrows(UnsupportedOperationException.class, () -> context.services().clear());
        assertThrows(IllegalArgumentException.class, () -> ApplicationContext.builder()
                .service(String.class, "first")
                .service(String.class, "second"));
    }

    @Test
    void starts_in_registration_order_stops_in_reverse_and_is_idempotent() {
        List<String> calls = new ArrayList<>();
        RecordingService first = new RecordingService("first", calls);
        RecordingService second = new RecordingService("second", calls);
        ApplicationContext context = ApplicationContext.builder()
                .service(FirstService.class, first)
                .service(SecondService.class, second)
                .build();

        context.start();
        context.start();
        context.stop();
        context.stop();

        assertEquals(List.of("first.start", "second.start", "second.stop", "first.stop"), calls);
        assertEquals(ApplicationContext.State.STOPPED, context.state());
        assertThrows(IllegalStateException.class, context::start);
    }

    @Test
    void manages_the_same_instance_registered_under_multiple_keys_once() {
        List<String> calls = new ArrayList<>();
        RecordingService service = new RecordingService("shared", calls);
        ApplicationContext context = ApplicationContext.builder()
                .service(FirstService.class, service)
                .service(ApplicationLifecycle.class, service)
                .build();

        context.start();
        context.close();

        assertEquals(List.of("shared.start", "shared.stop"), calls);
    }

    @Test
    void failed_start_rolls_back_started_services_and_suppresses_cleanup_failures() {
        List<String> calls = new ArrayList<>();
        RecordingService first = new RecordingService("first", calls);
        first.failStop = true;
        RecordingService second = new RecordingService("second", calls);
        second.failStart = true;
        ApplicationContext context = ApplicationContext.builder()
                .service(FirstService.class, first)
                .service(SecondService.class, second)
                .build();

        IllegalStateException failure = assertThrows(IllegalStateException.class, context::start);

        assertEquals("second start", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(List.of("first.start", "second.start", "second.stop", "first.stop"), calls);
        assertEquals(ApplicationContext.State.STOPPED, context.state());
    }

    @Test
    void shutdown_attempts_every_service_and_aggregates_failures() {
        List<String> calls = new ArrayList<>();
        RecordingService first = new RecordingService("first", calls);
        first.failStop = true;
        RecordingService second = new RecordingService("second", calls);
        second.failStop = true;
        ApplicationContext context = ApplicationContext.builder()
                .service(FirstService.class, first)
                .service(SecondService.class, second)
                .build();
        context.start();

        IllegalStateException failure = assertThrows(IllegalStateException.class, context::stop);

        assertEquals("second stop", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("first stop"));
        assertEquals(List.of("first.start", "second.start", "second.stop", "first.stop"), calls);
    }

    private interface FirstService extends ApplicationLifecycle {
    }

    private interface SecondService extends ApplicationLifecycle {
    }

    private static final class RecordingService implements FirstService, SecondService {
        private final String name;
        private final List<String> calls;
        private boolean failStart;
        private boolean failStop;

        private RecordingService(String name, List<String> calls) {
            this.name = name;
            this.calls = calls;
        }

        @Override
        public void start() {
            calls.add(name + ".start");
            if (failStart) {
                throw new IllegalStateException(name + " start");
            }
        }

        @Override
        public void stop() {
            calls.add(name + ".stop");
            if (failStop) {
                throw new IllegalStateException(name + " stop");
            }
        }
    }
}
