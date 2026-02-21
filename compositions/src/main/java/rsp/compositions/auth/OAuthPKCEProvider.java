package rsp.compositions.auth;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Contracts;
import rsp.compositions.routing.Router;
import rsp.dsl.Definition;
import rsp.page.events.RemoteCommand;
import rsp.server.http.HttpRequest;
import rsp.util.json.JsonDataType;
import rsp.util.json.JsonParser;
import rsp.util.json.JsonUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static rsp.dsl.Html.html;

/**
 * OAuth 2.0 PKCE authentication provider.
 * <p>
 * Implements the Authorization Code flow with PKCE (Proof Key for Code Exchange).
 * Works with any OAuth 2.0 / OpenID Connect provider (Keycloak, Auth0, etc.).
 * <p>
 * Session tracking uses the framework's {@code deviceId} cookie (long-lived, set by HttpHandler).
 * No additional cookies are needed.
 * <p>
 * Flow:
 * <ol>
 *   <li>User visits protected path → redirected to IdP authorization endpoint with PKCE challenge</li>
 *   <li>User authenticates at IdP → redirected to callback path with authorization code</li>
 *   <li>Provider exchanges code for access token, fetches userinfo, creates session</li>
 *   <li>User redirected to original path → session exists → authenticated</li>
 * </ol>
 */
public class OAuthPKCEProvider implements AuthComponent.AuthProvider {

    private static final String DEVICE_ID_COOKIE_NAME = "deviceId";

    private final System.Logger logger = System.getLogger(getClass().getName());
    private final JsonParser jsonParser = JsonUtils.createParser();
    private final OAuthConfig config;

    // deviceId → username
    private final ConcurrentMap<String, String> sessions = new ConcurrentHashMap<>();
    // OAuth state parameter → pending auth data
    private final ConcurrentMap<String, PendingAuth> pendingAuths = new ConcurrentHashMap<>();

    // Saved per-request for use in gateResponse()
    private HttpRequest savedRequest;
    private String savedDeviceId;

    public OAuthPKCEProvider(OAuthConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public AuthComponent.AuthResult authenticate(ComponentContext context) {
        savedRequest = context.get(HttpRequest.class);
        savedDeviceId = null;

        if (savedRequest == null) {
            return AuthComponent.AuthResult.anonymous();
        }

        List<String> deviceIds = savedRequest.cookies(DEVICE_ID_COOKIE_NAME);
        if (deviceIds.isEmpty()) {
            return AuthComponent.AuthResult.anonymous();
        }
        savedDeviceId = deviceIds.getFirst();

        String currentPath = savedRequest.path.toString();

        // Sign-out: remove session
        if (currentPath.startsWith(config.signOutPath())) {
            sessions.remove(savedDeviceId);
            return AuthComponent.AuthResult.anonymous();
        }

        // Check existing session
        String username = sessions.get(savedDeviceId);
        if (username != null) {
            return AuthComponent.AuthResult.authenticated(username);
        }

        return AuthComponent.AuthResult.anonymous();
    }

    @Override
    public Definition gateResponse(String currentPath) {
        // Login page: public — let the composition render it
        if (currentPath.startsWith(config.loginPath())) {
            return null;
        }

        // Callback: exchange code for token, create session, redirect to original URL
        if (currentPath.startsWith(config.callbackPath())) {
            return handleCallback();
        }

        // Sign-out path: redirect to root (will trigger login page again)
        if (currentPath.startsWith(config.signOutPath())) {
            return html().redirect("/");
        }

        // Sign-in trigger: start PKCE flow, redirect param has the original path
        if (currentPath.startsWith(config.signinPath())) {
            String redirect = savedRequest != null
                    ? savedRequest.queryParameters.parameterValue("redirect")
                    : null;
            return startPKCEFlow(redirect != null ? redirect : "/");
        }

        // Protected path: redirect to login page
        return html().redirect(config.loginPath() + "?redirect=" + currentPath);
    }

    @Override
    public boolean supportsSignOut() {
        return true;
    }

    @Override
    public void signOut(CommandsEnqueue commandsEnqueue) {
        commandsEnqueue.offer(new RemoteCommand.SetHref(config.signOutPath()));
    }

    /**
     * Creates the auth composition for the login page.
     * Register this composition before application compositions in the App.
     */
    public Composition authComposition() {
        final Router router = new Router()
                .route(config.loginPath(), LoginContract.class)
                .route(config.signinPath(), LoginContract.class)
                .route(config.callbackPath(), LoginContract.class)
                .route(config.signOutPath(), LoginContract.class);
        final Contracts contracts = new Contracts()
                .bind(LoginContract.class, LoginContract::new,
                      () -> new OAuthLoginComponent(config.signinPath()));
        return new Composition(router, contracts);
    }

    // ===== PKCE Flow =====

    private Definition startPKCEFlow(String currentPath) {
        if (savedDeviceId == null) {
            logger.log(System.Logger.Level.WARNING, "No deviceId cookie — cannot start PKCE flow");
            return html().redirect("/");
        }

        String codeVerifier = generateRandomString(64);
        String codeChallenge;
        try {
            codeChallenge = generateCodeChallenge(codeVerifier);
        } catch (NoSuchAlgorithmException e) {
            logger.log(System.Logger.Level.ERROR, "SHA-256 not available", e);
            return html().redirect("/");
        }

        String state = generateRandomString(16);
        pendingAuths.put(state, new PendingAuth(savedDeviceId, codeVerifier, currentPath));

        String authorizationUrl = config.authorizationEndpoint()
                + "?response_type=code"
                + "&client_id=" + config.clientId()
                + "&redirect_uri=" + encode(config.redirectUri())
                + "&scope=" + encode(config.scopes())
                + "&state=" + state
                + "&code_challenge=" + codeChallenge
                + "&code_challenge_method=S256";

        logger.log(System.Logger.Level.DEBUG, "Starting PKCE flow, redirecting to IdP");
        return html().redirect(authorizationUrl);
    }

    private Definition handleCallback() {
        if (savedRequest == null) {
            return html().redirect("/");
        }

        String state = savedRequest.queryParameters.parameterValue("state");
        String code = savedRequest.queryParameters.parameterValue("code");

        if (state == null || code == null) {
            logger.log(System.Logger.Level.WARNING, "Callback missing state or code parameter");
            return html().redirect("/");
        }

        PendingAuth pending = pendingAuths.remove(state);
        if (pending == null) {
            logger.log(System.Logger.Level.WARNING, "No pending auth for state: " + state);
            return html().redirect("/");
        }

        try {
            // Exchange authorization code for access token
            String accessToken = exchangeCodeForToken(code, pending.codeVerifier());
            if (accessToken == null) {
                logger.log(System.Logger.Level.ERROR, "Token exchange failed");
                return html().redirect("/");
            }

            // Fetch user info
            String username = fetchUsername(accessToken);
            if (username == null) {
                logger.log(System.Logger.Level.ERROR, "UserInfo fetch failed");
                return html().redirect("/");
            }

            // Create session
            sessions.put(pending.deviceId(), username);
            logger.log(System.Logger.Level.DEBUG, "OAuth session created for user: " + username);

            return html().redirect(pending.originalPath());
        } catch (Exception e) {
            logger.log(System.Logger.Level.ERROR, "OAuth callback error", e);
            return html().redirect("/");
        }
    }

    // ===== Token Exchange =====

    private String exchangeCodeForToken(String code, String codeVerifier) {
        StringBuilder body = new StringBuilder();
        body.append("grant_type=authorization_code");
        body.append("&client_id=").append(encode(config.clientId()));
        body.append("&redirect_uri=").append(encode(config.redirectUri()));
        body.append("&code=").append(encode(code));
        body.append("&code_verifier=").append(encode(codeVerifier));

        if (config.clientSecret() != null) {
            body.append("&client_secret=").append(encode(config.clientSecret()));
        }

        java.net.http.HttpRequest tokenRequest = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(config.tokenEndpoint()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        try (java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient()) {
            java.net.http.HttpResponse<String> response =
                    httpClient.send(tokenRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.log(System.Logger.Level.ERROR,
                        "Token endpoint returned " + response.statusCode() + ": " + response.body());
                return null;
            }

            JsonDataType tokenJson = jsonParser.parse(response.body());
            if (tokenJson instanceof JsonDataType.Object obj
                    && obj.value("access_token") instanceof JsonDataType.String(String accessToken)) {
                return accessToken;
            }

            logger.log(System.Logger.Level.ERROR, "Unexpected token response format");
            return null;
        } catch (IOException | InterruptedException e) {
            logger.log(System.Logger.Level.ERROR, "Token exchange HTTP error", e);
            return null;
        }
    }

    private String fetchUsername(String accessToken) {
        java.net.http.HttpRequest userInfoRequest = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(config.userInfoEndpoint()))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        try (java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient()) {
            java.net.http.HttpResponse<String> response =
                    httpClient.send(userInfoRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.log(System.Logger.Level.ERROR,
                        "UserInfo endpoint returned " + response.statusCode());
                return null;
            }

            JsonDataType userInfoJson = jsonParser.parse(response.body());
            if (userInfoJson instanceof JsonDataType.Object obj
                    && obj.value("preferred_username") instanceof JsonDataType.String(String username)) {
                return username;
            }

            logger.log(System.Logger.Level.ERROR, "Unexpected userinfo response format");
            return null;
        } catch (IOException | InterruptedException e) {
            logger.log(System.Logger.Level.ERROR, "UserInfo HTTP error", e);
            return null;
        }
    }

    // ===== PKCE Helpers =====

    private static String generateRandomString(int length) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String generateCodeChallenge(String codeVerifier) throws NoSuchAlgorithmException {
        byte[] bytes = codeVerifier.getBytes(StandardCharsets.US_ASCII);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(bytes, 0, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ===== Records =====

    /**
     * OAuth 2.0 PKCE configuration.
     *
     * @param authorizationEndpoint IdP authorization URL
     * @param tokenEndpoint         IdP token exchange URL
     * @param userInfoEndpoint      IdP userinfo URL
     * @param clientId              OAuth client ID
     * @param clientSecret          OAuth client secret (null for public clients)
     * @param redirectUri           full callback URL (e.g. "http://localhost:8085/auth/callback")
     * @param loginPath             path for the login page (e.g. "/auth/login")
     * @param signinPath            path that triggers PKCE flow (e.g. "/auth/signin")
     * @param callbackPath          path for the OAuth callback (e.g. "/auth/callback")
     * @param signOutPath           sign-out path (e.g. "/auth/signout")
     * @param scopes                OAuth scopes (e.g. "openid profile email")
     */
    public record OAuthConfig(
            String authorizationEndpoint,
            String tokenEndpoint,
            String userInfoEndpoint,
            String clientId,
            String clientSecret,
            String redirectUri,
            String loginPath,
            String signinPath,
            String callbackPath,
            String signOutPath,
            String scopes
    ) {
        public OAuthConfig {
            Objects.requireNonNull(authorizationEndpoint);
            Objects.requireNonNull(tokenEndpoint);
            Objects.requireNonNull(userInfoEndpoint);
            Objects.requireNonNull(clientId);
            // clientSecret may be null for public clients
            Objects.requireNonNull(redirectUri);
            Objects.requireNonNull(loginPath);
            Objects.requireNonNull(signinPath);
            Objects.requireNonNull(callbackPath);
            Objects.requireNonNull(signOutPath);
            Objects.requireNonNull(scopes);
        }
    }

    private record PendingAuth(String deviceId, String codeVerifier, String originalPath) {
        PendingAuth {
            Objects.requireNonNull(deviceId);
            Objects.requireNonNull(codeVerifier);
            Objects.requireNonNull(originalPath);
        }
    }
}
