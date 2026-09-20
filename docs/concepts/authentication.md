# Authentication

Authentication runs at the HTTP page boundary, before the framework creates a
component tree. The result is one immutable `Authentication` value containing
the application-defined principal and its roles. Components may display or
authorize against that value, but do not parse credentials or manage sessions.

Authentication and authorization are separate:

- `authentication-api` represents who made the request;
- `ui-http-auth` establishes that identity using HTTP Basic, an in-memory
  cookie session, or OAuth 2.0 PKCE;
- `authorization` decides whether an established subject may perform an
  action;
- `compositions` only projects identity into `ComponentContext` and can use it
  for block-mount access checks.

## Identity

```java
Authentication anonymous = Authentication.anonymous();
Authentication alice = Authentication.authenticated("alice", "admin", "editor");

alice.isAuthenticated(); // true
alice.hasRole("admin");  // true
```

Anonymous identity has no principal or roles. An authenticated identity must
have a non-null principal, and its role set is immutable. The principal is an
application-defined object; providers in this repository use a username.

`Authentication` is request/page-session data. Do not register it in
`ApplicationContext`, whose services are shared for the process.
`App.apply(...)` requires the value explicitly, including
`Authentication.anonymous()` for a deliberately public page.

## Attach An HTTP Provider

Providers accept application lifecycle separately from a callback that creates
the authenticated page result:

```java
SimpleAuthProvider auth = new SimpleAuthProvider();
App app = new App(context, List.of(loginComposition, postsComposition));

PageApplication pages = auth.pages(
        app,
        (request, authentication) ->
                PageResult.live(app.apply(request.relativeUrl(), authentication)));

WebServer server = new WebServer(8080)
        .pageApplication(pages)
        .routes(auth.routes());
```

This explicit adapter keeps `ui-http-auth` independent from `compositions`.
The provider authenticates the initial `HttpRequest`, applies its challenge or
redirect policy, and only then calls application code. The `App` projects the
result as `Authentication.class` in component context.

## Supplied Providers

- `BasicAuthProvider` validates an `Authorization: Basic` header on every page
  request and returns a `401` challenge when credentials are absent or invalid.
- `SimpleAuthProvider` is a development/demo in-memory session provider. Its
  sign-in endpoint creates the session and sets an `HttpOnly`, `SameSite=Lax`
  cookie; its sign-out endpoint invalidates the server session and expires the
  cookie.
- `OAuthPKCEProvider` owns authorization redirects, callback validation, token
  exchange, user-info lookup, server-side session expiry, and sign-out.

Session and OAuth endpoint paths are exposed by `auth.routes()` as ordinary,
method-aware `HttpRouter` routes. Add them to `WebServer`; they run
before UI page fallback and never create a component tree. Basic authentication
has no owned endpoints, so its route set is empty. Authentication endpoint paths
are matched exactly.
Post-authentication redirect values are restricted to same-origin path URLs.
The simple provider remains a demo facility: production deployments need a
persistent session store, credential controls, CSRF policy, TLS, key rotation,
and deployment-specific cookie settings.

## Presentation

The login block receives only a sign-in endpoint path. Clicking it navigates to
that endpoint; the block never creates a session or writes a cookie. Likewise,
`HeaderBlock` reads the immutable identity and renders an optional configured
sign-out link. It never holds or calls an authentication provider.

```java
new LoginBlock(auth.signInPath(), true);
new HeaderBlock(auth.signOutPath());
```

For custom components, read the snapshot directly:

```java
Authentication authentication = lookup().get(Authentication.class);
if (authentication != null && authentication.hasRole("admin")) {
    // render an admin capability
}
```

The snapshot belongs to the live page created by the authenticated initial
request. Changing credentials or signing out navigates through the HTTP
adapter and creates a new page; it does not mutate identity inside an existing
component tree.
