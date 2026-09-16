package rsp.compositions.auth;

import rsp.compositions.block.Block;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.compositions.composition.Composition;
import rsp.compositions.block.ContextKeys;
import rsp.compositions.block.SceneComponent;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * AuthComponent - Authentication context bridge.
 * <p>
 * This component:
 * 1. Reads authentication provider from context
 * 2. Reads the request identity selected by an outer adapter/middleware
 * 3. Enriches context with auth data (auth.user, auth.roles, auth.authenticated)
 * 4. Passes through to SceneComponent
 * <p>
 * Position in component chain: UrlSyncComponent → RoutingComponent → AuthComponent → SceneComponent
 * <p>
 * This is a pure framework component - no application-specific dependencies.
 */
public class AuthComponent extends Component<AuthComponent.AuthComponentState, Object> {

    public AuthComponent() {
        super();
    }

    @Override
    public ComponentStateSupplier<AuthComponentState> initStateSupplier() {
        return (_, context) -> {
            final AuthProvider authProvider = context.get(ContextKeys.AUTH_PROVIDER);
            final AuthResult authResult = context.get(ContextKeys.AUTH_RESULT);
            return stateFrom(context, authProvider,
                    authResult == null ? AuthResult.anonymous() : authResult);
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
    public ComponentView<AuthComponentState, Object> componentView() {
        return _ -> state -> {
            return new SceneComponent(state.path(),
                                      state.composition(),
                                      state.blockKey(),
                                      state.blockClass(),
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
                context.getRequired(ContextKeys.ROUTE_BLOCK_KEY),
                context.getRequired(ContextKeys.ROUTE_BLOCK_CLASS),
                context.getRequired(ContextKeys.ROUTE_PATH),
                context.getRequired(ContextKeys.ROUTE_PATTERN));
    }

    public record AuthComponentState(Object user,
                                     boolean authenticated,
                                     String[] roles,
                                     AuthProvider authProvider,
                                     Composition composition,
                                     Object blockKey,
                                     Class<? extends Block<?, ?>> blockClass,
                                     String path,
                                     String pattern) {
        public AuthComponentState {
            roles = roles != null ? roles.clone() : new String[0];
            Objects.requireNonNull(composition, "composition");
            Objects.requireNonNull(blockKey, "blockKey");
            Objects.requireNonNull(blockClass, "blockClass");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(pattern, "pattern");
        }

        @Override
        public String[] roles() {
            return roles.clone();
        }
    }

    /**
     * UI-side behavior exposed by an authentication integration.
     * Request authentication and access responses belong to the outer transport adapter.
     */
    public interface AuthProvider {
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
        public AuthResult {
            roles = roles == null ? new String[0] : roles.clone();
        }

        @Override
        public String[] roles() {
            return roles.clone();
        }

        public static AuthResult anonymous() {
            return new AuthResult(null, false, new String[0]);
        }

        public static AuthResult authenticated(Object user, String... roles) {
            return new AuthResult(user, true, roles);
        }
    }
}
