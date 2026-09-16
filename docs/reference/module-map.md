# Module Map

All framework artifacts use group ID `io.github.vadimv`. Direct project-module
dependencies are shown below; Maven resolves their transitive dependencies.

## Runtime Modules

| Artifact | Purpose | Direct project dependencies | Application use |
| --- | --- | --- | --- |
| `json` | JSON value model, parser, limits, and writer | none | Usually transitive |
| `url` | Immutable URL values and generic route templates/tables | none | Direct for framework-neutral routing |
| `http-api` | Transport-neutral HTTP request, response, headers, cookies, and bodies | `url` | Direct for REST applications and server adapters |
| `http-routing` | HTTP routing integration point | `http-api` | Reserved for method-aware REST routing |
| `http-json` | HTTP/JSON integration point | `http-api`, `json` | Reserved for JSON body codecs |
| `websocket-api` | UI-independent WebSocket contracts | none | Reserved for custom endpoints |
| `ui-core` | HTML DSL, components, DOM diffing, and live page sessions | `url`, `json` | Direct for custom UI adapters or low-level runtime use |
| `js-client` | Browser bridge sources and packaged client bundle | none | Usually transitive through `ui-http` |
| `authorization` | ABAC policies, attributes, and delegation grants | none | Direct for custom policies |
| `schema` | Field, validation, widget, and list-column metadata | none | Direct for schema-driven UI |
| `compositions` | Blocks, layouts, authentication context, and default list/form UI | `ui-core`, `url`, `authorization`, `schema` | Direct for routed admin applications |
| `ui-http` | Initial-page HTTP adapter plus the current embedded HTTP/1.1 and WebSocket server | `ui-core`, `http-api`; `js-client` at runtime | Direct for the built-in UI server |
| `ui-http-auth` | Basic, cookie-session, and OAuth PKCE page adapters | `ui-http`, `compositions`, `json` | Direct when using the supplied authentication providers |
| `server-jdk` | UI-neutral JDK transport destination | `http-api`, `websocket-api` | Reserved for the next transport extraction phase |

## Optional Extensions

| Artifact | Purpose | Direct project dependencies |
| --- | --- | --- |
| `ai-agent` | Agent runtime, model services, action dispatch, policies, and delegation | `ui-core`, `authorization`, `schema`, `compositions` |
| `agent-ui` | Prompt and delegation-approval blocks and views | `ui-core`, `compositions`, `authorization`, `ai-agent` |
| `telemetry` | Typed keys, timestamped samples, quality, subscriptions, registries, and command boundaries | none |
| `metrics-runtime` | Fixed framework metric registry with a read-only local JMX mirror | `ui-core` |
| `dashboard` | Immutable dashboard/widget definitions, telemetry DSL, renderer registry, block, and view | `ui-core`, `compositions`, `telemetry` |
| `ui-shell` | Explorer and header blocks and views | `ui-core`, `compositions` |

## Test Harnesses And Examples

| Artifact | Purpose | Publication role |
| --- | --- | --- |
| `pbt` | In-house property-based generators, shrinking, classification, and runner | Test dependency |
| `mutate` | Mutation engine and forked JUnit runner | Test dependency |
| `examples` | Runnable demonstrations and browser integration tests | Not an application dependency |

Start with `ui-http` for a minimal live application. Add `compositions` for routed
admin UI, then opt into extensions individually. See [getting started](../getting-started.md)
for dependency examples.
