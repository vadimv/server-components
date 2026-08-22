# Module Map

All framework artifacts use group ID `io.github.vadimv`. Direct project-module
dependencies are shown below; Maven resolves their transitive dependencies.

## Runtime Modules

| Artifact | Purpose | Direct project dependencies | Application use |
| --- | --- | --- | --- |
| `json` | JSON value model, parser, limits, and writer | none | Usually transitive |
| `core` | HTML DSL, components, DOM diffing, live sessions, and server-neutral HTTP types | `json` | For custom server adapters or low-level runtime use |
| `js-client` | Browser bridge sources and packaged client bundle | none | Usually transitive through `http` |
| `authorization` | ABAC policies, attributes, and delegation grants | none | Direct for custom policies |
| `schema` | Field, validation, widget, and list-column metadata | none | Direct for schema-driven UI |
| `compositions` | Routing, contracts, layouts, authentication, and default list/form UI | `core`, `authorization`, `schema` | Direct for routed admin applications |
| `http` | Embedded HTTP/1.1 and WebSocket server | `core`; `js-client` at runtime | Direct for the built-in server |

## Optional Extensions

| Artifact | Purpose | Direct project dependencies |
| --- | --- | --- |
| `ai-agent` | Agent runtime, model services, action dispatch, policies, and delegation | `core`, `authorization`, `schema`, `compositions` |
| `agent-ui` | Prompt and delegation-approval contracts and views | `core`, `compositions`, `authorization`, `ai-agent` |
| `dashboard` | Dashboard model, DSL, grid, widgets, contract, and view | `core`, `compositions` |
| `ui-shell` | Explorer and header contracts and views | `core`, `compositions` |

## Test Harnesses And Examples

| Artifact | Purpose | Publication role |
| --- | --- | --- |
| `pbt` | In-house property-based generators, shrinking, classification, and runner | Test dependency |
| `mutate` | Mutation engine and forked JUnit runner | Test dependency |
| `examples` | Runnable demonstrations and browser integration tests | Not an application dependency |

Start with `http` for a minimal live application. Add `compositions` for routed
admin UI, then opt into extensions individually. See [getting started](../getting-started.md)
for dependency examples.
