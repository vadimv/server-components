package rsp.compositions;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;

import java.util.HashMap;
import java.util.Map;
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
            // Read auth provider from context
            AuthProvider authProvider = (AuthProvider) context.getAttribute("auth.provider");

            if (authProvider == null) {
                // No auth provider configured - anonymous access
                return new AuthComponentState(null, false, new String[0]);
            }

            // Authenticate user
            AuthResult authResult = authProvider.authenticate(context);

            return new AuthComponentState(
                authResult.user(),
                authResult.authenticated(),
                authResult.roles()
            );
        };
    }

    @Override
    public BiFunction<ComponentContext, AuthComponentState, ComponentContext> subComponentsContext() {
        return (context, state) -> {
            Map<String, Object> authData = new HashMap<>();
            authData.put("auth.user", state.user());
            authData.put("auth.authenticated", state.authenticated());
            authData.put("auth.roles", state.roles());

            return context.with(authData);
        };
    }

    @Override
    public ComponentView<AuthComponentState> componentView() {
        // RoutingComponent reads from context
        return _ -> _ -> new RoutingComponent();
    }

    public record AuthComponentState(Object user, boolean authenticated, String[] roles) {
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
