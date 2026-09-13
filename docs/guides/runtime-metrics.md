# Runtime metrics and local JMX

RSP can keep a fixed set of framework health metrics in-process and mirror them
as read-only JMX attributes. This uses only JDK APIs and does not open a JMX or
telemetry listening port.

Create one runtime for the process and inject its metrics sink into the web
server:

```java
try (MetricsRuntime runtime = MetricsRuntime.withPlatformJmx()) {
    WebServer server = new WebServer(8080, request -> rootComponent(), runtime.metrics());
    server.start();
    server.join();
}
```

The same `MetricRegistry` backs component, HTTP, WebSocket, and resumable-page
metrics. Its catalogs are fixed allow-lists: unknown names and type mismatches
are rejected, and adapters cannot enumerate request data, session identifiers,
exception messages, or application object graphs.

Applications can append their own fixed, reviewed descriptors before creating
the runtime:

```java
MetricCatalog catalog = MetricNames.frameworkCatalog().with(
        MetricDescriptor.counter("app.jobs.completed", "1", "Completed jobs", "AppJobsCompleted"));
MetricsRuntime runtime = MetricsRuntime.withPlatformJmx(catalog);
```

The web server installs that process-wide `Metrics` instance into every root
component context. Application code can resolve it without making telemetry a
required dependency of the component:

```java
Metrics metrics = Metrics.from(componentContext);
Metrics metrics = Metrics.from(lookup);
```

Both forms return `Metrics.noop()` when no runtime was configured. Application
entry points may instead use ordinary constructor injection when they already
own the runtime. The `Counter` example does this and declares a monotonic
`app.counter.increments` metric. Every page has independent UI state, while all
accepted increment intents update the same process-wide counter. Instrument the
intent or operation boundary rather than rendering callbacks, which can execute
more than once for a single application operation.

Values and descriptions should remain aggregate and must not encode user,
tenant, request, session, payload, or exception data.

## Lifecycle-bound metric objects

Use a `MetricObject` when one runtime-owned instance needs its own numeric
state, such as a mounted component or a live WebSocket connection. A
`MetricObjectType` declares the schema up front; opening an object cannot add
names or attributes at runtime:

```java
MetricDescriptor currentValue = MetricDescriptor.gauge(
        "app.counter.value", "1", "Current counter value", "CurrentValue");
MetricObjectType counterType = new MetricObjectType(
        "app.counter", "Counter", MetricCatalog.of(currentValue));

MetricObjectCatalog objectCatalog =
        MetricObjectTypes.frameworkCatalog().with(counterType);
MetricsRuntime runtime = MetricsRuntime.withPlatformJmx(
        MetricNames.frameworkCatalog(), objectCatalog);
```

A component can obtain its object from the live segment in a segment-aware
lifecycle callback:

```java
@Override
public void onMounted(ComponentSegment<Integer> segment,
                      ComponentCompositeKey componentId,
                      Integer state,
                      CommandsEnqueue commands,
                      StateUpdater<Integer> stateUpdater) {
    segment.metricObject(counterType).setGauge(currentValue.name(), state);
}
```

Repeated lookup of the same type returns the same object for that segment. The
segment closes all of its objects after `onUnmounted`; a failed initial render
also cleans up objects opened by an earlier lifecycle callback. Code that owns
objects outside a segment should use try-with-resources or close them in its own
destroy path.

JMX mirrors each live object under the aggregate MBean's domain, using only its
fixed type and an opaque, monotonically allocated per-type process-local ordinal. For
example, live counters appear as
`rsp.metrics:type=Counter,instance=0` and
`rsp.metrics:type=Counter,instance=1`. The ordinal is deliberately not a user,
session, request, component, or connection identifier. A
`WebSocketConnection,instance=0` can coexist with `Counter,instance=0` because
the type is part of the MBean identity. Objects expose only the numeric
attributes in their declared schema and no management operations.

The registry permits at most 1,024 live objects by default. Opens beyond that
limit return a disabled handle and increment `MetricObjectsRejected`. Aggregate
metrics also report created and active objects and adapter failures. Closed
objects disappear from snapshots and JMX immediately; their ordinals are not
reused by that object type during the process lifetime.

The HTTP runtime creates one framework metric object for each live WebSocket
connection. It contains only received/sent message and payload-byte counters.
Control-frame contents, addresses, URLs, headers, session identifiers, close
reasons, and payloads are never represented.

## Manual acceptance with JConsole

1. Run `rsp.app.Counter` from the IDE. From a shell, the equivalent commands are:

   ```shell
   mvn -pl examples -am install -DskipTests
   mvn -pl examples exec:java -Dexec.mainClass=rsp.app.Counter
   ```

2. Start the JDK `jconsole` application and select the local Counter JVM.
3. Open **MBeans → rsp.metrics → Framework → Attributes**.
4. Refresh the attributes while loading `http://localhost:8080` in one or more
   browser sessions and interacting with the counter page. `AppCounterIncrements`
   should equal the total number of increment actions across those sessions;
   `HttpRequests`, `SegmentCreated`, and the active gauges should also change.
5. Open two counter pages and inspect the live `Counter` instances under
   **MBeans → rsp.metrics**. Each has an independent `CurrentValue`. A live
   `WebSocketConnection` instance also reports only message and byte counts.
   Closing a page connection removes the WebSocket object. A counter object is
   removed when its server-side component is actually unmounted; it may remain
   while the page is retained for same-process WebSocket resume.
6. Stop the application and verify that the local process disappears from
   JConsole.

Local attach is controlled by the operating-system user boundary. Do not enable
the JVM's remote JMX connector unless the deployment explicitly supplies its own
authentication, transport encryption, and network policy.

This phase intentionally has no OTLP endpoint and no outbound exporter. A later
adapter can read immutable aggregate and object snapshots without changing
instrumentation sites or the JMX view. In an OTLP projection, an object remains
a metric time series with bounded, adapter-defined attributes; it does not
become an arbitrary application object or resource.
