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

import java.util.Objects;
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

    public AuthComponent() {
        super();
    }

    @Override
    public ComponentStateSupplier<AuthComponentState> initStateSupplier() {
        return (_, context) -> {
            final AuthProvider authProvider = context.get(ContextKeys.AUTH_PROVIDER);
            if (authProvider == null) {
                // No auth provider configured - anonymous access
                return stateFrom(context, authProvider, AuthResult.anonymous());
            }

            // Authenticate user
            return stateFrom(context, authProvider, authProvider.authenticate(context));
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
            if (state.authProvider() != null && !state.authenticated()) {
                Definition gate = state.authProvider().gateResponse(state.path());
                if (gate != null) {
                    return gate;
                }
            }

            // Pass through — create SceneComponent from routing context
            return new SceneComponent(state.path(),
                                      state.composition(),
                                      state.contractClass(),
                                      state.pattern(),
                                      state.composition().layout());
        };
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    private static AuthComponentState stateFrom(ComponentContext context,
                                                AuthProvider authProvider,
                                                AuthResult authResult) {
        Objects.requireNonNull(authResult, "authResult");
        return new AuthComponentState(
                authResult.user(),
                authResult.authenticated(),
                authResult.roles(),
                authProvider,
                context.getRequired(ContextKeys.ROUTE_COMPOSITION),
                context.getRequired(ContextKeys.ROUTE_CONTRACT_CLASS),
                context.getRequired(ContextKeys.ROUTE_PATH),
                context.getRequired(ContextKeys.ROUTE_PATTERN));
    }

    public record AuthComponentState(Object user,
                                     boolean authenticated,
                                     String[] roles,
                                     AuthProvider authProvider,
                                     Composition composition,
                                     Class<? extends ViewContract> contractClass,
                                     String path,
                                     String pattern) {
        public AuthComponentState {
            roles = roles != null ? roles.clone() : new String[0];
            Objects.requireNonNull(composition, "composition");
            Objects.requireNonNull(contractClass, "contractClass");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(pattern, "pattern");
        }

        @Override
        public String[] roles() {
            return roles.clone();
        }
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
