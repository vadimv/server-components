package rsp.compositions;

import java.util.Locale;

/**
 * AppConfig - Application configuration.
 *
 * <p><strong>Two Supported Configuration Patterns:</strong></p>
 *
 * <h3>Pattern A: Single AppConfig (Simpler - Recommended for trusted codebases)</h3>
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
