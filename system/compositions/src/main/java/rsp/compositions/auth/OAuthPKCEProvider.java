package rsp.compositions.auth;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.routing.Router;
import rsp.dsl.Definition;
import rsp.page.events.RemoteCommand;
import rsp.server.http.HttpRequest;
import rsp.util.json.JsonDataType;
import rsp.util.json.JsonParser;
import rsp.util.json.JsonUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Flow;

import static rsp.dsl.Html.html;

/**
 * OAuth 2.0 PKCE authentication provider.
 * <p>
 * Implements the Authorization Code flow with PKCE (Proof Key for Code Exchange).
 * Works with any OAuth 2.0 / OpenID Connect provider (Keycloak, Auth0, etc.).
 * <p>
 * The framework's long-lived {@code deviceId} cookie is used only to bind a
 * pending PKCE flow to the browser that started it. Authenticated sessions use
 * a separate HTTP-set cookie so the auth credential can be rotated, expired,
 * and protected with {@code HttpOnly}.
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
    public static final String SESSION_COOKIE_NAME = "rsp_oauth_session";
    private static final int SESSION_MAX_AGE_SECONDS = 60 * 60 * 8;

    /**
     * Maximum size of an OAuth endpoint response body we will buffer. Token and userinfo responses
     * are small; this bounds memory against a misbehaving or compromised IdP returning an unbounded
     * (or chunked, length-lying) body. Exceeding it aborts the read with an {@link IOException}.
     */
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final System.Logger logger = System.getLogger(getClass().getName());
    private final JsonParser jsonParser = JsonUtils.createParser();
    private final OAuthConfig config;
    private final Clock clock;
    private final long sessionMaxAgeSeconds;

    // auth session token → session data
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
    // OAuth state parameter → pending auth data
    private final ConcurrentMap<String, PendingAuth> pendingAuths = new ConcurrentHashMap<>();

    public OAuthPKCEProvider(OAuthConfig config) {
        this(config, Clock.systemUTC(), SESSION_MAX_AGE_SECONDS);
    }

    OAuthPKCEProvider(OAuthConfig config, Clock clock, long sessionMaxAgeSeconds) {
        this.config = Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
        if (sessionMaxAgeSeconds <= 0) {
            throw new IllegalArgumentException("sessionMaxAgeSeconds must be positive");
        }
        this.sessionMaxAgeSeconds = sessionMaxAgeSeconds;
    }

    @Override
    public AuthComponent.AuthResult authenticate(ComponentContext context) {
        HttpRequest request = context.get(HttpRequest.class);
        if (request == null) {
            return AuthComponent.AuthResult.anonymous();
        }

        String token = authSessionToken(request);
        if (token == null) {
            return AuthComponent.AuthResult.anonymous();
        }

        Session session = sessions.get(token);
        if (session == null) {
            return AuthComponent.AuthResult.anonymous();
        }
        if (session.isExpired(clock.instant())) {
            sessions.remove(token, session);
            return AuthComponent.AuthResult.anonymous();
        }

        return AuthComponent.AuthResult.authenticated(session.username());
    }

    @Override
    public Definition gateResponse(ComponentContext context, AuthComponent.AuthResult authResult) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(authResult, "authResult");

        HttpRequest request = context.get(HttpRequest.class);
        String currentPath = context.getRequired(ContextKeys.ROUTE_PATH);

        // Login page: public — let the composition render it
        if (currentPath.startsWith(config.loginPath())) {
            return null;
        }

        // Callback: exchange code for token, create session, redirect to original URL
        if (currentPath.startsWith(config.callbackPath())) {
            return handleCallback(request);
        }

        // Sign-out path: clear auth session and redirect to root (will trigger login page again)
        if (currentPath.startsWith(config.signOutPath())) {
            return handleSignOut(request);
        }

        // Sign-in trigger: start PKCE flow, redirect param has the original path
        if (currentPath.startsWith(config.signinPath())) {
            String redirect = request != null
                    ? request.queryParameters.parameterValue("redirect")
                    : null;
            return startPKCEFlow(request, safeLocalRedirect(redirect));
        }

        if (authResult.authenticated()) {
            return null;
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
        final Group group = new Group()
                .bind(LoginContract.class, () -> new LoginContract(config.signinPath()));
        return new Composition(router, new DefaultLayout(), group);
    }

    // ===== PKCE Flow =====

    private Definition startPKCEFlow(HttpRequest request, String currentPath) {
        String deviceId = deviceId(request);
        if (deviceId == null) {
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
        pendingAuths.put(state, new PendingAuth(deviceId, codeVerifier, safeLocalRedirect(currentPath)));

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

    private Definition handleCallback(HttpRequest request) {
        if (request == null) {
            return html().redirect("/");
        }

        String state = request.queryParameters.parameterValue("state");
        String code = request.queryParameters.parameterValue("code");

        if (state == null || code == null) {
            logger.log(System.Logger.Level.WARNING, "Callback missing state or code parameter");
            return html().redirect("/");
        }

        PendingAuth pending = pendingAuths.remove(state);
        if (pending == null) {
            logger.log(System.Logger.Level.WARNING, "No pending auth for state: " + state);
            return html().redirect("/");
        }

        String deviceId = deviceId(request);
        if (deviceId == null || !deviceId.equals(pending.deviceId())) {
            logger.log(System.Logger.Level.WARNING, "Callback deviceId does not match pending auth");
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
            String sessionToken = createSession(username);
            logger.log(System.Logger.Level.DEBUG, "OAuth session created for user: " + username);

            return html().redirect(pending.originalPath())
                    .addHeader("Set-Cookie", sessionCookie(sessionToken));
        } catch (Exception e) {
            logger.log(System.Logger.Level.ERROR, "OAuth callback error", e);
            return html().redirect("/");
        }
    }

    private Definition handleSignOut(HttpRequest request) {
        if (request != null) {
            String token = authSessionToken(request);
            if (token != null) {
                sessions.remove(token);
            }
        }
        return html().redirect("/")
                .addHeader("Set-Cookie", expiredSessionCookie());
    }

    String createSession(String username) {
        Objects.requireNonNull(username, "username");
        String sessionToken = generateRandomString(32);
        sessions.put(sessionToken, new Session(username, clock.instant().plusSeconds(sessionMaxAgeSeconds)));
        return sessionToken;
    }

    private String authSessionToken(HttpRequest request) {
        if (request == null) {
            return null;
        }
        List<String> cookies = request.cookies(SESSION_COOKIE_NAME);
        return cookies.isEmpty() ? null : cookies.getFirst();
    }

    private String deviceId(HttpRequest request) {
        if (request == null) {
            return null;
        }
        List<String> deviceIds = request.cookies(DEVICE_ID_COOKIE_NAME);
        return deviceIds.isEmpty() ? null : deviceIds.getFirst();
    }

    private String sessionCookie(String token) {
        return SESSION_COOKIE_NAME + "=" + token
                + "; Path=/"
                + "; Max-Age=" + sessionMaxAgeSeconds
                + "; HttpOnly"
                + "; SameSite=Lax"
                + secureCookieAttribute();
    }

    private String expiredSessionCookie() {
        return SESSION_COOKIE_NAME + "="
                + "; Path=/"
                + "; Max-Age=0"
                + "; HttpOnly"
                + "; SameSite=Lax"
                + secureCookieAttribute();
    }

    private String secureCookieAttribute() {
        return config.redirectUri().startsWith("https://") ? "; Secure" : "";
    }

    /**
     * Normalizes user-controlled OAuth redirect targets to same-origin path URLs.
     * External, protocol-relative, malformed, or header-unsafe values fall back to root.
     */
    static String safeLocalRedirect(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return "/";
        }

        final String candidate = redirect.trim();
        if (!candidate.startsWith("/")
                || candidate.startsWith("//")
                || candidate.indexOf('\\') >= 0
                || candidate.chars().anyMatch(ch -> Character.isISOControl(ch) || Character.isWhitespace(ch))) {
            return "/";
        }

        try {
            final URI uri = URI.create(candidate);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawPath() == null) {
                return "/";
            }
            return candidate;
        } catch (IllegalArgumentException _) {
            return "/";
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
                    httpClient.send(tokenRequest, boundedStringBodyHandler(MAX_RESPONSE_BYTES));

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
                    httpClient.send(userInfoRequest, boundedStringBodyHandler(MAX_RESPONSE_BYTES));

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

    // ===== Bounded response reading =====

    /**
     * A {@link HttpResponse.BodyHandler} that reads the body as a UTF-8 string but aborts once more
     * than {@code maxBytes} have arrived, so a misbehaving endpoint cannot exhaust the heap.
     *
     * @param maxBytes the maximum number of body bytes to buffer
     * @return a body handler producing the decoded string, or failing with {@link IOException}
     */
    private static HttpResponse.BodyHandler<String> boundedStringBodyHandler(final int maxBytes) {
        return responseInfo -> HttpResponse.BodySubscribers.mapping(
                new BoundedByteSubscriber(maxBytes),
                bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    /** Collects body bytes, cancelling the subscription if the configured cap is exceeded. */
    static final class BoundedByteSubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int maxBytes;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int total;

        BoundedByteSubscriber(final int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(final Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(final List<ByteBuffer> items) {
            for (final ByteBuffer item : items) {
                final int remaining = item.remaining();
                if (total + remaining > maxBytes) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("Response body exceeds " + maxBytes + " bytes"));
                    return;
                }
                total += remaining;
                final byte[] chunk = new byte[remaining];
                item.get(chunk);
                buffer.writeBytes(chunk);
            }
        }

        @Override
        public void onError(final Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(buffer.toByteArray());
        }
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

    private record Session(String username, Instant expiresAt) {
        Session {
            Objects.requireNonNull(username);
            Objects.requireNonNull(expiresAt);
        }

        boolean isExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }
}
