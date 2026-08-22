# API And Javadocs

Public Java API documentation is generated per Maven module. From the repository
root, run:

```bash
mvn -DskipTests package
```

Each built module writes browsable Javadocs to its `target/apidocs/` directory
and attaches a `*-javadoc.jar`. For example:

- `system/core/target/apidocs/index.html`
- `system/compositions/target/apidocs/index.html`
- `system/http/target/apidocs/index.html`
- `extensions/ai-agent/target/apidocs/index.html`

Generated files are build artifacts and are not committed. Use the
[module map](module-map.md) to identify the owning artifact, then consult its
Javadocs for signatures. Narrative behavior and supported combinations remain
in the concepts, guides, and reference pages because generated API comments do
not replace architectural documentation.
