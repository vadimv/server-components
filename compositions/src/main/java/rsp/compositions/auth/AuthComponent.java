package rsp.compositions.auth;

import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.compositions.composition.Composition;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.SceneComponent;
import rsp.compositions.contract.ViewContract;

import java.util.Set;
import java.util.function.BiFunction;

import static rsp.dsl.Html.html;

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
        return (context, state) -> {
            return context
                .with(ContextKeys.AUTH_USER, state.user())
                .with(ContextKeys.AUTH_AUTHENTICATED, state.authenticated())
                .with(ContextKeys.AUTH_ROLES, state.roles());
        };
    }

    @Override
    public ComponentView<AuthComponentState> componentView() {
        return _ -> state -> {
            String loginPath = authProvider != null ? authProvider.loginPath() : null;
            if (loginPath != null && !state.authenticated()) {
                String currentPath = savedContext.get(ContextKeys.ROUTE_PATH);
                boolean isPublic = authProvider.publicPathPrefixes().stream()
                        .anyMatch(currentPath::startsWith);
                if (!isPublic) {
                    return html().redirect(loginPath + "?redirect=" + currentPath);
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
     * Override {@link #loginPath()} and {@link #publicPathPrefixes()} to enable the auth gate.
     * When {@code loginPath()} returns null (default), the gate is disabled.
     */
    public interface AuthProvider {
        AuthResult authenticate(ComponentContext context);

        /**
         * Path to redirect to when authentication is required.
         * Return null to disable the gate (pass through unconditionally).
         */
        default String loginPath() {
            return null;
        }

        /**
         * Path prefixes that bypass the auth gate (e.g., login/callback pages).
         */
        default Set<String> publicPathPrefixes() {
            return Set.of();
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
