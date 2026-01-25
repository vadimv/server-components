package rsp.compositions.auth;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.routing.UrlSyncComponent;
import rsp.server.http.HttpRequest;

import java.util.function.BiFunction;

/**
 * AuthComponent - Authentication layer (Framework Layer #2).
 * <p>
 * This component:
 * 1. Reads authentication provider from context
 * 2. Authenticates user from session/cookies/headers
 * 3. Enriches context with auth data (auth.user, auth.roles, auth.authenticated)
 * <p>
 * This is a pure framework component - no application-specific dependencies.
 */
public class AuthComponent extends Component<AuthComponent.AuthComponentState> {

    public AuthComponent() {
        super();
    }

    @Override
    public ComponentStateSupplier<AuthComponentState> initStateSupplier() {
        return (_, context) -> {
            // Read HttpRequest from context (needed for UrlSyncComponent)
            HttpRequest httpRequest = context.get(HttpRequest.class);

            // Read auth provider from context
            AuthProvider authProvider = context.get(ContextKeys.AUTH_PROVIDER);

            if (authProvider == null) {
                // No auth provider configured - anonymous access
                return new AuthComponentState(httpRequest, null, false, new String[0]);
            }

            // Authenticate user
            AuthResult authResult = authProvider.authenticate(context);

            return new AuthComponentState(
                httpRequest,
                authResult.user(),
                authResult.authenticated(),
                authResult.roles()
            );
        };
    }

    @Override
    public BiFunction<ComponentContext, AuthComponentState, ComponentContext> subComponentsContext() {
        return (context, state) -> {
            return context
                .with(ContextKeys.AUTH_USER, state.user())
                .with(ContextKeys.AUTH_AUTHENTICATED, state.authenticated())
                .with(ContextKeys.AUTH_ROLES, state.roles());
        };
    }

    @Override
    public ComponentView<AuthComponentState> componentView() {
        // UrlSyncComponent populates url.path, url.query.*, etc. into context
        // Then renders RoutingComponent which reads from context
        return _ -> state -> new UrlSyncComponent(state.httpRequest().relativeUrl());
    }

    public record AuthComponentState(HttpRequest httpRequest, Object user, boolean authenticated, String[] roles) {
    }

    /**
     * AuthProvider interface - implement to provide custom authentication.
     */
    public interface AuthProvider {
        AuthResult authenticate(ComponentContext context);
    }

    /**
     * AuthResult - result of authentication attempt.
     */
    public record AuthResult(Object user, boolean authenticated, String[] roles) {
        public static AuthResult anonymous() {
            return new AuthResult(null, false, new String[0]);
        }

        public static AuthResult authenticated(Object user, String... roles) {
            return new AuthResult(user, true, roles);
        }
    }
}
