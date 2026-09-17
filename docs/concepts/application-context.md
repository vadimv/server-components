# Application Context And Lifecycle

`application-api` provides process-level configuration, typed services, and
lifecycle without depending on HTTP, WebSocket, or UI modules. One
`ApplicationContext` represents one running application instance.

## Build A Context

```java
ApplicationConfig config = new ApplicationConfig()
        .with(defaultProperties)
        .with(System.getProperties());

ApplicationContext context = ApplicationContext.builder()
        .config(config)
        .service(MailService.class, mailService)
        .service(JobScheduler.class, scheduler)
        .build();
```

Service keys are explicit and type-checked. Duplicate keys are rejected during
assembly, and the built registry is immutable. Use `get(...)` for an optional
service and `require(...)` for a mandatory one.

Configuration layers are immutable and use last-writer-wins semantics. The
strict integer and boolean accessors report missing or malformed required
values during assembly rather than silently selecting a default.

Compositions project configuration into component context. Keep credentials
and other values that components must not observe out of `ApplicationConfig`;
inject them directly into the service instance that needs them before
registration.

## Lifecycle

A registered service may implement `ApplicationLifecycle`. The context starts
those services once in registration order and stops them once in reverse order.
Aliases of the same object are managed once.

Startup is transactional. If a service fails to start, the context stops every
service whose startup was attempted, in reverse order. Cleanup failures are
suppressed on the startup failure. Normal shutdown similarly attempts every
service and aggregates failures. A stopped context cannot be restarted; build
a new context for a new application instance.

Component mounts, page sessions, HTTP requests, and route changes never start
or stop application services. Resources scoped to one component still belong
in component lifecycle callbacks.

## REST Hosting

Attach a context to a transport-neutral HTTP application, then let the server
bracket it:

```java
HttpRouter routes = HttpRouter.builder()
        .get("/health", HttpRouteHandler.sync((request, route) ->
                HttpResponse.ok().text("ok").build()))
        .build();

HttpApplication application = HttpApplication.withLifecycle(context, routes);
JdkWebServer server = new JdkWebServer(8080, application);
server.start();
server.join();
```

The context is running before the listening socket accepts requests. Shutdown
first drains HTTP and WebSocket work, then stops application services.

## UI Hosting

The compositions adapter projects configuration and services into each page's
immutable `ComponentContext`, but does not own their lifecycle:

```java
App app = new App(context, List.of(authComposition, postsComposition));
PageApplication pages = authProvider.pages(app);
WebServer server = WebServer.pages(8080, pages);
server.start();
server.join();
```

Authentication adapters preserve the `App` lifecycle. On shutdown, the UI host
closes WebSockets and live page sessions before it stops the application
context, so component cleanup can still use application services.
