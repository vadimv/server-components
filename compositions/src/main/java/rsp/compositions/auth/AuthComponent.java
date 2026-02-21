package rsp.compositions.auth;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.compositions.composition.Composition;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.SceneComponent;
import rsp.compositions.contract.ViewContract;

import rsp.dsl.Definition;

import java.util.function.BiFunction;

/**
 * AuthComponent - Authentication gate.
 * <p>
 * This component:
 * 1. Reads authentication provider from context
 * 2. Authenticates user from session/cookies/headers
 * 3. Enriches context with auth data (auth.user, auth.roles, auth.authenticated)
 * 4. Gates access: redirects to login path when not authenticated on protected routes
 * 5. Passes through to SceneComponent when authenticated or on public routes
 * <p>
 * Position in component chain: UrlSyncComponent → RoutingComponent → AuthComponent → SceneComponent
 * <p>
 * This is a pure framework component - no application-specific dependencies.
 */
public class AuthComponent extends Component<AuthComponent.AuthComponentState> {

    private ComponentContext savedContext;
    private AuthProvider authProvider;

    public AuthComponent() {
        super();
    }

    @Override
    public ComponentStateSupplier<AuthComponentState> initStateSupplier() {
        return (_, context) -> {
            this.savedContext = context;

            // Read auth provider from context
            this.authProvider = context.get(ContextKeys.AUTH_PROVIDER);

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
        return (context, state) -> context
                .with(ContextKeys.AUTH_USER, state.user())
                .with(ContextKeys.AUTH_AUTHENTICATED, state.authenticated())
                .with(ContextKeys.AUTH_ROLES, state.roles());
    }

    @Override
    public ComponentView<AuthComponentState> componentView() {
        return _ -> state -> {
            if (authProvider != null && !state.authenticated()) {
                String currentPath = savedContext.get(ContextKeys.ROUTE_PATH);
                Definition gate = authProvider.gateResponse(currentPath);
                if (gate != null) {
                    return gate;
                }
            }

            // Pass through — create SceneComponent from routing context
            Composition composition = savedContext.get(ContextKeys.ROUTE_COMPOSITION);
            Class<? extends ViewContract> contractClass = savedContext.get(ContextKeys.ROUTE_CONTRACT_CLASS);
            String path = savedContext.get(ContextKeys.ROUTE_PATH);
            String pattern = savedContext.get(ContextKeys.ROUTE_PATTERN);
            return new SceneComponent(path,
                                      composition,
                                      contractClass,
                                      pattern,
                                      composition.layout());
        };
    }

    public record AuthComponentState(Object user, boolean authenticated, String[] roles) {
    }

    /**
     * AuthProvider interface - implement to provide custom authentication.
     * <p>
     * Override {@link #gateResponse(String)} to control what happens when a user
     * is not authenticated on a protected route. The default implementation returns null (no gate).
     */
    public interface AuthProvider {
        AuthResult authenticate(ComponentContext context);

        /**
         * Returns the response to render when the user is not authenticated on the given path.
         * Return null to allow access (no gate, or public path).
         * <p>
         * Examples:
         * <ul>
         *   <li>Redirect to login page: {@code html().redirect("/login?redirect=" + currentPath)}</li>
         *   <li>HTTP Basic challenge: {@code html().statusCode(401).addHeader("WWW-Authenticate", "Basic realm=\"app\"")}</li>
         * </ul>
         */
        default Definition gateResponse(String currentPath) {
            return null;
        }

        /**
         * Whether this provider supports sign-out.
         * When true, a "Sign out" button is shown and {@link #signOut(CommandsEnqueue)} is called on click.
         * Default: false (no sign-out button).
         */
        default boolean supportsSignOut() {
            return false;
        }

        /**
         * Performs sign-out by sending commands to the browser.
         * Called when the user clicks "Sign out".
         */
        default void signOut(CommandsEnqueue commandsEnqueue) {
        }
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
