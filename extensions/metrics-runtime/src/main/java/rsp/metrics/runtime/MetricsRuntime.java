package rsp.metrics.runtime;

import rsp.metrics.MetricCatalog;
import rsp.metrics.MetricNames;
import rsp.metrics.MetricRegistry;
import rsp.metrics.Metrics;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns a production metric registry and its read-only JMX mirror.
 *
 * <p>Registering with the platform MBean server does not start a JMX network
 * connector. Local JVM attach remains available; remote JMX requires explicit
 * JVM configuration by the application operator.</p>
 */
public final class MetricsRuntime implements AutoCloseable {
    /** Default JMX object name used by {@link #withPlatformJmx()}. */
    public static final String DEFAULT_OBJECT_NAME = "rsp.metrics:type=Framework";

    private final MetricRegistry registry;
    private final MBeanServer mBeanServer;
    private final ObjectName objectName;
    private final AtomicBoolean closed = new AtomicBoolean();

    private MetricsRuntime(final MetricCatalog catalog,
                           final MBeanServer mBeanServer,
                           final ObjectName objectName) {
        registry = new MetricRegistry(Objects.requireNonNull(catalog, "catalog"));
        this.mBeanServer = Objects.requireNonNull(mBeanServer, "mBeanServer");
        this.objectName = Objects.requireNonNull(objectName, "objectName");
        try {
            mBeanServer.registerMBean(new MetricsDynamicMBean(registry), objectName);
        } catch (final InstanceAlreadyExistsException
                       | MBeanRegistrationException
                       | NotCompliantMBeanException ex) {
            throw new IllegalStateException("Could not register framework metrics MBean", ex);
        }
    }

    /** Registers the framework catalog with the local platform MBean server. */
    public static MetricsRuntime withPlatformJmx() {
        return withPlatformJmx(MetricNames.frameworkCatalog());
    }

    /** Registers a catalog with the local platform MBean server. */
    public static MetricsRuntime withPlatformJmx(final MetricCatalog catalog) {
        try {
            return withJmx(catalog,
                           ManagementFactory.getPlatformMBeanServer(),
                           new ObjectName(DEFAULT_OBJECT_NAME));
        } catch (final MalformedObjectNameException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    /** Registers a catalog with an explicitly supplied MBean server and object name. */
    public static MetricsRuntime withJmx(final MetricCatalog catalog,
                                         final MBeanServer mBeanServer,
                                         final ObjectName objectName) {
        return new MetricsRuntime(catalog, mBeanServer, objectName);
    }

    /** Returns the sink to inject into framework entry points such as a web server. */
    public Metrics metrics() {
        return registry;
    }

    /** Returns the live registry for snapshots and future outbound adapters. */
    public MetricRegistry registry() {
        return registry;
    }

    /** Unregisters this runtime's MBean. This method is idempotent. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            mBeanServer.unregisterMBean(objectName);
        } catch (final javax.management.InstanceNotFoundException ignored) {
            // Already unregistered by the owning application.
        } catch (final MBeanRegistrationException ex) {
            throw new IllegalStateException("Could not unregister framework metrics MBean", ex);
        }
    }
}
