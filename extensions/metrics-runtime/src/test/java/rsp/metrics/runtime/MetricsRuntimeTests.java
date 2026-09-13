package rsp.metrics.runtime;

import org.junit.jupiter.api.Test;
import rsp.metrics.MetricCatalog;
import rsp.metrics.MetricDescriptor;
import rsp.metrics.MetricNames;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsRuntimeTests {
    @Test
    void default_factory_registers_with_the_platform_mbean_server() throws Exception {
        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        final ObjectName name = new ObjectName(MetricsRuntime.DEFAULT_OBJECT_NAME);
        assertFalse(server.isRegistered(name));

        try (MetricsRuntime ignored = MetricsRuntime.withPlatformJmx()) {
            assertTrue(server.isRegistered(name));
        }

        assertFalse(server.isRegistered(name));
    }

    @Test
    void mirrors_live_registry_values_as_read_only_jmx_attributes() throws Exception {
        final MBeanServer server = MBeanServerFactory.createMBeanServer();
        final ObjectName name = new ObjectName("test.metrics:type=Framework");

        try (MetricsRuntime runtime = MetricsRuntime.withJmx(MetricNames.frameworkCatalog(), server, name)) {
            assertTrue(server.isRegistered(name));
            assertEquals(0L, server.getAttribute(name, "HttpRequests"));

            runtime.metrics().incrementCounter(MetricNames.HTTP_REQUESTS, 3);
            runtime.metrics().setGauge(MetricNames.PAGE_SESSIONS_ACTIVE, 2);

            assertEquals(3L, server.getAttribute(name, "HttpRequests"));
            assertEquals(2L, server.getAttribute(name, "PageSessionsActive"));
            assertFalse(server.getMBeanInfo(name).getAttributes()[0].isWritable());
            assertThrows(AttributeNotFoundException.class,
                         () -> server.setAttribute(name, new Attribute("HttpRequests", 99L)));
        }

        assertFalse(server.isRegistered(name));
    }

    @Test
    void exposes_only_catalogued_numeric_attributes_and_no_operations() throws Exception {
        final MBeanServer server = MBeanServerFactory.createMBeanServer();
        final ObjectName name = new ObjectName("test.metrics:type=Boundary");

        try (MetricsRuntime ignored = MetricsRuntime.withJmx(MetricNames.frameworkCatalog(), server, name)) {
            final var info = server.getMBeanInfo(name);

            assertEquals(MetricNames.frameworkCatalog().descriptors().size(), info.getAttributes().length);
            for (final var attribute : info.getAttributes()) {
                assertEquals(Long.class.getName(), attribute.getType());
                assertTrue(attribute.isReadable());
                assertFalse(attribute.isWritable());
            }
            assertEquals(0, info.getOperations().length);
            assertEquals(0, info.getNotifications().length);
            assertThrows(AttributeNotFoundException.class,
                         () -> server.getAttribute(name, "SessionId"));
        }
    }

    @Test
    void mirrors_catalogued_application_metrics() throws Exception {
        final MBeanServer server = MBeanServerFactory.createMBeanServer();
        final ObjectName name = new ObjectName("test.metrics:type=Application");
        final MetricDescriptor increments = MetricDescriptor.counter(
                "app.counter.increments",
                "1",
                "Counter increments handled across all page sessions",
                "AppCounterIncrements");
        final MetricCatalog catalog = MetricNames.frameworkCatalog().with(increments);

        try (MetricsRuntime runtime = MetricsRuntime.withJmx(catalog, server, name)) {
            runtime.metrics().incrementCounter(increments.name(), 3);

            assertEquals(3L, server.getAttribute(name, increments.jmxAttribute()));
        }
    }

    @Test
    void close_is_idempotent() throws Exception {
        final MBeanServer server = MBeanServerFactory.createMBeanServer();
        final ObjectName name = new ObjectName("test.metrics:type=Close");
        final MetricsRuntime runtime = MetricsRuntime.withJmx(MetricNames.frameworkCatalog(), server, name);

        runtime.close();
        runtime.close();

        assertFalse(server.isRegistered(name));
    }
}
