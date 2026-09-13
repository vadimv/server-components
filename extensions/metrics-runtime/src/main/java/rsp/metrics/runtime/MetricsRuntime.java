package rsp.metrics.runtime;

import rsp.metrics.MetricCatalog;
import rsp.metrics.MetricNames;
import rsp.metrics.MetricObject;
import rsp.metrics.MetricObjectCatalog;
import rsp.metrics.MetricObjectListener;
import rsp.metrics.MetricObjectTypes;
import rsp.metrics.MetricRegistry;
import rsp.metrics.Metrics;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<MetricObjectKey, ObjectName> metricObjectNames = new ConcurrentHashMap<>();
    private final MetricObjectListener.Registration objectListenerRegistration;
    private final AtomicBoolean closed = new AtomicBoolean();

    private MetricsRuntime(final MetricCatalog catalog,
                           final MetricObjectCatalog objectCatalog,
                           final int maxActiveMetricObjects,
                           final MBeanServer mBeanServer,
                           final ObjectName objectName) {
        registry = new MetricRegistry(Objects.requireNonNull(catalog, "catalog"),
                                      Objects.requireNonNull(objectCatalog, "objectCatalog"),
                                      maxActiveMetricObjects);
        this.mBeanServer = Objects.requireNonNull(mBeanServer, "mBeanServer");
        this.objectName = Objects.requireNonNull(objectName, "objectName");
        try {
            mBeanServer.registerMBean(new MetricsDynamicMBean(registry), objectName);
        } catch (final InstanceAlreadyExistsException
                       | MBeanRegistrationException
                       | NotCompliantMBeanException ex) {
            throw new IllegalStateException("Could not register metrics MBean", ex);
        }
        objectListenerRegistration = registry.registerMetricObjectListener(new MetricObjectListener() {
            @Override
            public void onOpened(final MetricObject object) {
                registerMetricObject(object);
            }

            @Override
            public void onClosed(final MetricObject object) {
                unregisterMetricObject(object);
            }
        });
    }

    /** Registers the framework catalog with the local platform MBean server. */
    public static MetricsRuntime withPlatformJmx() {
        return withPlatformJmx(MetricNames.frameworkCatalog());
    }

    /** Registers a catalog with the local platform MBean server. */
    public static MetricsRuntime withPlatformJmx(final MetricCatalog catalog) {
        return withPlatformJmx(catalog, MetricObjectTypes.frameworkCatalog());
    }

    /** Registers aggregate and lifecycle-bound object catalogs with local JMX. */
    public static MetricsRuntime withPlatformJmx(final MetricCatalog catalog,
                                                 final MetricObjectCatalog objectCatalog) {
        return withPlatformJmx(catalog,
                               objectCatalog,
                               MetricRegistry.DEFAULT_MAX_ACTIVE_METRIC_OBJECTS);
    }

    /** Registers catalogs with local JMX and applies an active-object limit. */
    public static MetricsRuntime withPlatformJmx(final MetricCatalog catalog,
                                                 final MetricObjectCatalog objectCatalog,
                                                 final int maxActiveMetricObjects) {
        try {
            return withJmx(catalog,
                           objectCatalog,
                           maxActiveMetricObjects,
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
        return withJmx(catalog,
                       MetricObjectTypes.frameworkCatalog(),
                       mBeanServer,
                       objectName);
    }

    /** Registers aggregate and object catalogs with an explicitly supplied JMX server. */
    public static MetricsRuntime withJmx(final MetricCatalog catalog,
                                         final MetricObjectCatalog objectCatalog,
                                         final MBeanServer mBeanServer,
                                         final ObjectName objectName) {
        return withJmx(catalog,
                       objectCatalog,
                       MetricRegistry.DEFAULT_MAX_ACTIVE_METRIC_OBJECTS,
                       mBeanServer,
                       objectName);
    }

    /** Registers aggregate and lifecycle-bound metric objects with an explicit JMX server. */
    public static MetricsRuntime withJmx(final MetricCatalog catalog,
                                         final MetricObjectCatalog objectCatalog,
                                         final int maxActiveMetricObjects,
                                         final MBeanServer mBeanServer,
                                         final ObjectName objectName) {
        return new MetricsRuntime(catalog,
                                  objectCatalog,
                                  maxActiveMetricObjects,
                                  mBeanServer,
                                  objectName);
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
        registry.closeMetricObjects();
        objectListenerRegistration.close();
        try {
            mBeanServer.unregisterMBean(objectName);
        } catch (final javax.management.InstanceNotFoundException ignored) {
            // Already unregistered by the owning application.
        } catch (final MBeanRegistrationException ex) {
            throw new IllegalStateException("Could not unregister metrics MBean", ex);
        }
    }

    private void registerMetricObject(final MetricObject object) {
        final ObjectName metricObjectName = metricObjectName(object);
        try {
            mBeanServer.registerMBean(new MetricsDynamicMBean(object), metricObjectName);
            metricObjectNames.put(metricObjectKey(object), metricObjectName);
        } catch (final InstanceAlreadyExistsException
                       | MBeanRegistrationException
                       | NotCompliantMBeanException ex) {
            throw new IllegalStateException("Could not register metric-object MBean", ex);
        }
    }

    private void unregisterMetricObject(final MetricObject object) {
        final ObjectName metricObjectName = metricObjectNames.remove(metricObjectKey(object));
        if (metricObjectName == null) {
            return;
        }
        try {
            mBeanServer.unregisterMBean(metricObjectName);
        } catch (final javax.management.InstanceNotFoundException ignored) {
            // Already unregistered by the owning application.
        } catch (final MBeanRegistrationException ex) {
            throw new IllegalStateException("Could not unregister metric-object MBean", ex);
        }
    }

    private ObjectName metricObjectName(final MetricObject object) {
        final Hashtable<String, String> properties = new Hashtable<>();
        properties.put("type", object.type().jmxType());
        properties.put("instance", Long.toString(object.instanceNumber()));
        try {
            return new ObjectName(objectName.getDomain(), properties);
        } catch (final MalformedObjectNameException ex) {
            throw new IllegalStateException("Could not create metric-object MBean name", ex);
        }
    }

    private static MetricObjectKey metricObjectKey(final MetricObject object) {
        return new MetricObjectKey(object.type().name(), object.instanceNumber());
    }

    private record MetricObjectKey(String typeName, long instanceNumber) {
    }
}
