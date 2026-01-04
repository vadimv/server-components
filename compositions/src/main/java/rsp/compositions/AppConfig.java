package rsp.compositions;

import java.util.Locale;

/**
 * AppConfig - Application configuration.
 *
 * <p><strong>Two Supported Configuration Patterns:</strong></p>
 *
 * <h2>Pattern A: Single AppConfig (Simpler - Recommended for trusted codebases)</h2>
 * <ul>
 *   <li>AppConfig contains BOTH sensitive (DB credentials) and non-sensitive (page size) fields</li>
 *   <li>Services read sensitive fields during initialization: {@code new PostService(appConfig.getDatabaseConfig())}</li>
 *   <li>AppConfig flows through ComponentContext</li>
 *   <li>Contracts read non-sensitive fields: {@code appConfig.defaultPageSize()}</li>
 *   <li><strong>Trust model:</strong> Developers are trusted not to accidentally log/render sensitive fields from contracts</li>
 * </ul>
 *
 * <h3>Pattern B: Separate ServiceConfig + AppConfig (Defensive - Guard rails)</h3>
 * <ul>
 *   <li>Create separate {@code ServiceConfig} class for sensitive data (DB credentials, API keys)</li>
 *   <li>ServiceConfig used ONLY during service initialization, NEVER added to ComponentContext</li>
 *   <li>AppConfig contains ONLY non-sensitive fields, flows through ComponentContext</li>
 *   <li><strong>Benefit:</strong> Impossible to accidentally access secrets from contracts (they're not in context)</li>
 * </ul>
 *
 * <p><strong>Security Policy (applies to both patterns):</strong></p>
 * <ul>
 *   <li>✅ <strong>ALWAYS:</strong> Secrets are passed to services during initialization, never exposed after that</li>
 *   <li>✅ <strong>ALWAYS:</strong> Service instances (not their config) are added to ComponentContext</li>
 *   <li>❌ <strong>Pattern A caveat:</strong> If using single AppConfig with secrets, developers must never
 *       log/render sensitive fields from contracts (requires discipline)</li>
 *   <li>❌ <strong>Pattern B caveat:</strong> More boilerplate (two config classes)</li>
 * </ul>
 *
 * <p><strong>Philosophical Note:</strong> In a 100% trusted codebase (all internal code), there's no security
 * risk from having secrets in context - it's about preventing <em>accidental mistakes</em> (debug logging),
 * not security against malicious code. Choose the pattern that fits your team's discipline level.</p>
 *
 * @param defaultPageSize Default number of items per page in list views
 * @param maxPageSize Maximum allowed page size
 * @param dateFormat Default date format string (e.g., "yyyy-MM-dd")
 * @param locale Application locale
 * @param enableDebugMode Enable debug logging
 */
public record AppConfig(
    int defaultPageSize,
    int maxPageSize,
    String dateFormat,
    Locale locale,
    boolean enableDebugMode
) {
    /**
     * Default application configuration (non-sensitive fields only in this example).
     *
     * <p>When using Pattern A with secrets, add sensitive fields here and document
     * which methods should NEVER be called from contracts (e.g., getDatabaseConfig()).</p>
     */
    public static AppConfig defaults() {
        return new AppConfig(
            10,           // defaultPageSize
            100,          // maxPageSize
            "yyyy-MM-dd", // dateFormat
            Locale.US,    // locale
            false         // enableDebugMode
        );
    }

    /**
     * Load application configuration from Java system properties or environment variables.
     *
     * <p>Configuration sources (in order of precedence):</p>
     * <ol>
     *   <li>Java system properties (-D flags)</li>
     *   <li>Environment variables</li>
     *   <li>Default values</li>
     * </ol>
     *
     * <table border="1" >
     *   <caption>Supported properties and environment variables:</caption>
     *   <tr><th>Property</th><th>Env Variable</th><th>Default</th></tr>
     *   <tr><td>app.pageSize.default</td><td>APP_PAGE_SIZE_DEFAULT</td><td>10</td></tr>
     *   <tr><td>app.pageSize.max</td><td>APP_PAGE_SIZE_MAX</td><td>100</td></tr>
     *   <tr><td>app.dateFormat</td><td>APP_DATE_FORMAT</td><td>yyyy-MM-dd</td></tr>
     *   <tr><td>app.locale</td><td>APP_LOCALE</td><td>en_US</td></tr>
     *   <tr><td>app.debugMode</td><td>APP_DEBUG_MODE</td><td>false</td></tr>
     * </table>
     *
     * <p>Example usage:</p>
     * <pre>{@code
     * // From command line (system properties):
     * java -Dapp.pageSize.default=20 -Dapp.debugMode=true -jar app.jar
     *
     * // From environment variables:
     * export APP_PAGE_SIZE_DEFAULT=20
     * export APP_DEBUG_MODE=true
     * java -jar app.jar
     *
     * // In code:
     * AppConfig config = AppConfig.fromSystemProperties();
     * }</pre>
     *
     * @return AppConfig populated from system properties/env vars with fallback to defaults
     */
    public static AppConfig fromSystemProperties() {
        int defaultPageSize = Integer.parseInt(getConfigValue("app.pageSize.default", "APP_PAGE_SIZE_DEFAULT", "10"));
        int maxPageSize = Integer.parseInt(getConfigValue("app.pageSize.max", "APP_PAGE_SIZE_MAX", "100"));
        String dateFormat = getConfigValue("app.dateFormat", "APP_DATE_FORMAT", "yyyy-MM-dd");
        String localeStr = getConfigValue("app.locale", "APP_LOCALE", "en_US");
        boolean debugMode = Boolean.parseBoolean(getConfigValue("app.debugMode", "APP_DEBUG_MODE", "false"));

        Locale locale = parseLocale(localeStr);

        return new AppConfig(defaultPageSize, maxPageSize, dateFormat, locale, debugMode);
    }

    /**
     * Get configuration value from system property or environment variable.
     * System properties take precedence over environment variables.
     *
     * @param propertyName System property name (e.g., "app.pageSize.default")
     * @param envVarName Environment variable name (e.g., "APP_PAGE_SIZE_DEFAULT")
     * @param defaultValue Default value if neither is set
     * @return Configuration value from first available source
     */
    private static String getConfigValue(String propertyName, String envVarName, String defaultValue) {
        // 1. Check system property first (highest precedence)
        String value = System.getProperty(propertyName);
        if (value != null) {
            return value;
        }

        // 2. Check environment variable second
        value = System.getenv(envVarName);
        if (value != null) {
            return value;
        }

        // 3. Return default value
        return defaultValue;
    }

    /**
     * Parse locale string in format "language_COUNTRY" (e.g., "en_US", "de_DE").
     * Uses modern Locale.Builder API to avoid deprecated constructors.
     */
    private static Locale parseLocale(String localeStr) {
        if (localeStr == null || localeStr.isEmpty()) {
            return Locale.US;
        }

        String[] parts = localeStr.split("_");
        Locale.Builder builder = new Locale.Builder();

        try {
            return switch (parts.length) {
                case 1 -> builder.setLanguage(parts[0]).build();
                case 2 -> builder.setLanguage(parts[0]).setRegion(parts[1]).build();
                case 3 -> builder.setLanguage(parts[0]).setRegion(parts[1]).setVariant(parts[2]).build();
                default -> Locale.US;
            };
        } catch (Exception e) {
            // If parsing fails, return default locale
            return Locale.US;
        }
    }

    public AppConfig {
        if (defaultPageSize < 1) {
            throw new IllegalArgumentException("defaultPageSize must be >= 1");
        }
        if (maxPageSize < defaultPageSize) {
            throw new IllegalArgumentException("maxPageSize must be >= defaultPageSize");
        }
    }

    // Future extension for Pattern A (single config with secrets):
    // public DatabaseConfig getDatabaseConfig() { return databaseConfig; }
    // ↑ If added, document in JavaDoc: "ONLY call during service initialization, NEVER from contracts"
}
