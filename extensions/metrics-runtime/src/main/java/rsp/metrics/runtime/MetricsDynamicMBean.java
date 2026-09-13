package rsp.metrics.runtime;

import rsp.metrics.MetricDescriptor;
import rsp.metrics.MetricRegistry;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ReflectionException;
import java.util.Objects;

/** Catalog-driven, read-only numeric projection of a metric registry. */
final class MetricsDynamicMBean implements DynamicMBean {
    private final MetricRegistry registry;
    private final MBeanInfo mBeanInfo;

    MetricsDynamicMBean(final MetricRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
        final MBeanAttributeInfo[] attributes = registry.catalog().descriptors().stream()
                .map(MetricsDynamicMBean::attributeInfo)
                .toArray(MBeanAttributeInfo[]::new);
        mBeanInfo = new MBeanInfo(MetricsDynamicMBean.class.getName(),
                                  "Read-only RSP framework metrics",
                                  attributes,
                                  new MBeanConstructorInfo[0],
                                  new MBeanOperationInfo[0],
                                  new MBeanNotificationInfo[0]);
    }

    @Override
    public Object getAttribute(final String attribute)
            throws AttributeNotFoundException, MBeanException, ReflectionException {
        if (attribute == null) {
            throw new AttributeNotFoundException("Unknown metric attribute");
        }
        final MetricDescriptor descriptor = registry.catalog()
                .descriptorForJmxAttribute(attribute)
                .orElseThrow(() -> new AttributeNotFoundException("Unknown metric attribute"));
        return registry.value(descriptor.name());
    }

    @Override
    public void setAttribute(final Attribute attribute)
            throws AttributeNotFoundException, InvalidAttributeValueException,
                   MBeanException, ReflectionException {
        throw new AttributeNotFoundException("Metric attributes are read-only");
    }

    @Override
    public AttributeList getAttributes(final String[] attributes) {
        final AttributeList result = new AttributeList();
        if (attributes == null) {
            return result;
        }
        for (final String attribute : attributes) {
            try {
                result.add(new Attribute(attribute, getAttribute(attribute)));
            } catch (final AttributeNotFoundException | MBeanException | ReflectionException ignored) {
                // DynamicMBean bulk reads omit unknown attributes by contract.
            }
        }
        return result;
    }

    @Override
    public AttributeList setAttributes(final AttributeList attributes) {
        return new AttributeList();
    }

    @Override
    public Object invoke(final String actionName,
                         final Object[] params,
                         final String[] signature) throws MBeanException, ReflectionException {
        throw new ReflectionException(new NoSuchMethodException(), "No metric operations are exposed");
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        return mBeanInfo;
    }

    private static MBeanAttributeInfo attributeInfo(final MetricDescriptor descriptor) {
        return new MBeanAttributeInfo(descriptor.jmxAttribute(),
                                      Long.class.getName(),
                                      descriptor.description()
                                      + " [kind=" + descriptor.kind()
                                      + ", unit=" + descriptor.unit() + "]",
                                      true,
                                      false,
                                      false);
    }
}
