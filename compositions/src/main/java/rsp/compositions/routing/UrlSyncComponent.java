package rsp.compositions.routing;

import rsp.component.ComponentView;
import rsp.component.definitions.AutoAddressBarSyncComponent;
import rsp.server.http.RelativeUrl;

/**
 * UrlSyncComponent - Synchronizes browser URL with component context.
 * <p>
 * This component:
 * 1. Populates context with URL data (url.path, url.path.*, url.query.*, url.fragment)
 * 2. Handles bidirectional sync between context changes and browser address bar
 * 3. Renders RoutingComponent which reads path from context (not HttpRequest)
 * <p>
 * Position in component chain: AuthComponent → UrlSyncComponent → RoutingComponent
 */
public class UrlSyncComponent extends AutoAddressBarSyncComponent {

    public UrlSyncComponent(RelativeUrl initialRelativeUrl) {
        super(initialRelativeUrl);
    }

    @Override
    public ComponentView<RelativeUrl> componentView() {
        // RoutingComponent reads url.path from context (populated by parent's subComponentsContext)
        return _ -> _ -> new RoutingComponent();
    }
}
