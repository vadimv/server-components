package rsp.metrics.runtime;

import org.junit.jupiter.api.Test;
import rsp.metrics.MetricCatalog;
import rsp.metrics.MetricDescriptor;
import rsp.metrics.MetricNames;
import rsp.metrics.MetricObject;
import rsp.metrics.MetricObjectCatalog;
import rsp.metrics.MetricObjectType;

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
    private static final MetricDescriptor CURRENT_VALUE = MetricDescriptor.gauge(
            "test.counter.value", "1", "Current value", "CurrentValue");
    private static final MetricObjectType COUNTER_TYPE = new MetricObjectType(
            "test.counter", "Counter", MetricCatalog.of(CURRENT_VALUE));
    private static final MetricDescriptor MESSAGES = MetricDescriptor.counter(
            "test.connection.messages", "1", "Messages", "Messages");
    private static final MetricObjectType CONNECTION_TYPE = new MetricObjectType(
            "test.connection", "Connection", MetricCatalog.of(MESSAGES));

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
    void registers_one_read_only_mbean_per_live_metric_object() throws Exception {
        final MBeanServer server = MBeanServerFactory.createMBeanServer();
        final ObjectName aggregateName = new ObjectName("test.metrics:type=Framework");
        final ObjectName firstName = new ObjectName("test.metrics:type=Counter,instance=0");
        final ObjectName secondName = new ObjectName("test.metrics:type=Counter,instance=1");
        final ObjectName connectionName = new ObjectName("test.metrics:type=Connection,instance=0");

        try (MetricsRuntime runtime = MetricsRuntime.withJmx(
                MetricNames.frameworkCatalog(),
                MetricObjectCatalog.of(COUNTER_TYPE, CONNECTION_TYPE),
                10,
                server,
                aggregateName)) {
            final MetricObject first = runtime.metrics().openObject(COUNTER_TYPE);
            final MetricObject connection = runtime.metrics().openObject(CONNECTION_TYPE);
            final MetricObject second = runtime.metrics().openObject(COUNTER_TYPE);
            first.setGauge(CURRENT_VALUE.name(), 7);
            connection.incrementCounter(MESSAGES.name(), 3);
            second.setGauge(CURRENT_VALUE.name(), 11);

            assertTrue(server.isRegistered(firstName));
            assertTrue(server.isRegistered(secondName));
            assertTrue(server.isRegistered(connectionName));
            assertEquals(7L, server.getAttribute(firstName, CURRENT_VALUE.jmxAttribute()));
            assertEquals(11L, server.getAttribute(secondName, CURRENT_VALUE.jmxAttribute()));
            assertEquals(3L, server.getAttribute(connectionName, MESSAGES.jmxAttribute()));
            assertEquals(1, server.getMBeanInfo(firstName).getAttributes().length);
            assertFalse(server.getMBeanInfo(firstName).getAttributes()[0].isWritable());
            assertEquals(0, server.getMBeanInfo(firstName).getOperations().length);
            assertEquals("Counter", firstName.getKeyProperty("type"));
            assertEquals("0", firstName.getKeyProperty("instance"));
            assertEquals(2, firstName.getKeyPropertyList().size());
            assertThrows(AttributeNotFoundException.class,
                         () -> server.getAttribute(firstName, "SessionId"));

            first.close();

            assertFalse(server.isRegistered(firstName));
            assertTrue(server.isRegistered(secondName));
            assertTrue(server.isRegistered(connectionName));
        }

        assertFalse(server.isRegistered(aggregateName));
        assertFalse(server.isRegistered(secondName));
        assertFalse(server.isRegistered(connectionName));
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
