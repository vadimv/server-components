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
4. Refresh the attributes while loading `http://localhost:8080` and interacting
   with the counter page. `HttpRequests`, `SegmentCreated`, and the active gauges
   should change.
5. Stop the application and verify that the local process disappears from
   JConsole.

Local attach is controlled by the operating-system user boundary. Do not enable
the JVM's remote JMX connector unless the deployment explicitly supplies its own
authentication, transport encryption, and network policy.

This phase intentionally has no OTLP endpoint and no outbound exporter. A later
adapter can read immutable registry snapshots without changing instrumentation
sites or the JMX view.
