package rsp.http;

import rsp.http.routing.HttpRouteCatalog;

/**
 * Immutable HTTP route graph accepted by {@link WebServer}.
 *
 * <p>A router may contain ordinary HTTP handlers and UI page handlers. The
 * server adapts both kinds to its transport-level request dispatcher.</p>
 */
public sealed interface Router extends HttpRouteCatalog permits HttpRouter {
}
