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
metrics. Its catalog is a fixed allow-list: unknown names and type mismatches are
rejected, and adapters cannot enumerate request data, session identifiers,
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
5. Stop the application and verify that the local process disappears from
   JConsole.

Local attach is controlled by the operating-system user boundary. Do not enable
the JVM's remote JMX connector unless the deployment explicitly supplies its own
authentication, transport encryption, and network policy.

This phase intentionally has no OTLP endpoint and no outbound exporter. A later
adapter can read immutable registry snapshots without changing instrumentation
sites or the JMX view.
