# Getting Started

Use the first path to evaluate RSP from its source tree. The second explains
which locally installed Maven artifacts an application needs.

## Requirements

- Java 25
- Maven 3.8.7 or newer

The project does not currently include a Maven wrapper.

## Build And Run The Examples

```bash
git clone https://github.com/vadimv/server-components.git
cd server-components
mvn clean install
```

Start with the counter:

```bash
mvn exec:java -pl examples -Dexec.mainClass=rsp.app.Counter
```

Open <http://localhost:8080>. Its complete source is
[Counter.java](../examples/src/main/java/rsp/app/Counter.java).

The full admin application adds routing, generated list and form UI,
authentication, dashboards, and human-approved agent actions:

```bash
mvn exec:java -pl examples -Dexec.mainClass=rsp.app.posts.CrudApp
```

Open <http://localhost:8085> and select **Sign in**. `CrudApp` uses the local,
deterministic `RegexAgentService` by default, so this path makes no external
model calls.

## Select Maven Dependencies

`mvn install` publishes the current `3.1.0-SNAPSHOT` artifacts to your local
Maven repository. A minimal live application needs `http`; it brings in the
core runtime and browser client:

```xml
<dependency>
    <groupId>io.github.vadimv</groupId>
    <artifactId>http</artifactId>
    <version>3.1.0-SNAPSHOT</version>
</dependency>
```

Add `compositions` for routed admin applications:

```xml
<dependency>
    <groupId>io.github.vadimv</groupId>
    <artifactId>compositions</artifactId>
    <version>3.1.0-SNAPSHOT</version>
</dependency>
```

Optional features are separate artifacts: `ai-agent`, `agent-ui`, `dashboard`,
and `ui-shell`. See the [module map](reference/module-map.md) before adding them.

The repository currently documents source and locally installed snapshot use.
Check the root `pom.xml` for the current version when working from another
revision.

## Use A Real Agent Backend

The admin example accepts `regex`, `claude`, or `ollama` through `ai.agent`:

```bash
mvn exec:java -pl examples -Dexec.mainClass=rsp.app.posts.CrudApp \
    -Dai.agent=claude

mvn exec:java -pl examples -Dexec.mainClass=rsp.app.posts.CrudApp \
    -Dai.agent=ollama
```

Claude reads `ANTHROPIC_API_KEY`. The optional `rsp.agent.model` property selects
the Claude or Ollama model, `rsp.agent.url` overrides the Ollama endpoint, and
`rsp.agent.timeoutSeconds` controls the backend timeout.

## Next Steps

- Choose another runnable application from the [examples catalog](examples.md).
- Learn the [core runtime](concepts/core.md).
- Continue with [compositions](concepts/compositions.md) for routed applications.
- Review the [HTTP server's limits](reference/http-server.md) before deployment.
