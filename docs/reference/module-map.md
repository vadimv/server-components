# Module Map

All framework artifacts use group ID `io.github.vadimv`. Direct project-module
dependencies are shown below; Maven resolves their transitive dependencies.

## Runtime Modules

| Artifact | Purpose | Direct project dependencies | Application use |
| --- | --- | --- | --- |
| `application-api` | Immutable configuration, typed application services, and process lifecycle | none | Direct for shared REST or UI application resources |
| `json` | JSON value model, parser, limits, and writer | none | Usually transitive |
| `url` | Immutable URL values and generic route templates/tables | none | Direct for framework-neutral routing |
| `http-api` | Transport-neutral HTTP request, response, headers, cookies, bodies, and lifecycle-aware applications | `application-api`, `url` | Direct for REST applications and server adapters |
| `http-routing` | Immutable, method-aware REST router over generic URL route tables | `http-api` | Direct for routed REST applications |
| `http-json` | JSON request validation, decoding, responses, and error envelopes | `http-api`, `json` | Direct for JSON HTTP APIs |
| `websocket-api` | UI-independent endpoint, session, listener, error, and close-code contracts | `http-api` | Direct for custom WebSocket endpoints |
| `ui-core` | HTML DSL, components, DOM diffing, and live page sessions | `url`, `json` | Direct for custom UI adapters or low-level runtime use |
| `js-client` | Browser bridge sources and packaged client bundle | none | Usually transitive through `ui-http` |
| `authorization` | ABAC policies, attributes, and delegation grants | none | Direct for custom policies |
| `schema` | Field, validation, widget, and list-column metadata | none | Direct for schema-driven UI |
| `compositions` | Blocks, layouts, application-context projection, authentication context, and default list/form UI | `application-api`, `ui-core`, `url`, `authorization`, `schema` | Direct for routed admin applications |
| `ui-http` | Initial-page and resumable RSP adapters over the generic JDK server | `application-api`, `ui-core`, `http-api`, `websocket-api`, `server-jdk`; `js-client` at runtime | Direct for the built-in UI server |
| `ui-http-auth` | Basic, cookie-session, and OAuth PKCE page adapters | `ui-http`, `compositions`, `json` | Direct when using the supplied authentication providers |
| `server-jdk` | UI-neutral JDK HTTP/1.1 and RFC 6455 socket transport | `http-api`, `websocket-api` | Direct for embedded REST and custom WebSocket servers |

## Optional Extensions

| Artifact | Purpose | Direct project dependencies |
| --- | --- | --- |
| `ai-agent` | Agent runtime, model services, action dispatch, policies, and delegation | `ui-core`, `authorization`, `schema`, `compositions` |
| `agent-ui` | Prompt and delegation-approval blocks, views, and lifecycle-managed prompt service | `application-api`, `ui-core`, `compositions`, `authorization`, `ai-agent` |
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

Start with `ui-http` for a live UI application. For a REST-only application,
combine `server-jdk` with `http-routing` and optionally `http-json`; none of
those modules pulls in the UI runtime. Add `compositions` for routed admin UI,
then opt into extensions individually. See [getting started](../getting-started.md)
for dependency examples.
