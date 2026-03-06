package rsp.compositions.application;

import rsp.component.ComponentContext;
import rsp.component.ContextKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Universal configuration provider with layered merge semantics.
 *
 * <p>Config is an immutable {@code String → String} map loaded from Java {@link Properties}
 * and merged by priority. Properties are auto-mapped to the Context namespace
 * so they can be consumed by contracts via {@code Lookup} typed accessors.</p>
 *
 * <p><strong>Merge rule:</strong> "last writer wins" — chain {@code .with()} from most general
 * to most specific. Later values override earlier ones for the same key.</p>
 *
 * <pre>{@code
 * Config config = new Config()
 *     .with(getGeneralProperties())         // base defaults (lowest priority)
 *     .with(getHostSpecificProperties())    // host overrides
 *     .with(System.getProperties());        // JVM -D flags (highest priority)
 * }</pre>
 *
 * <p><strong>Context integration:</strong> Use {@link #applyTo(ComponentContext)} to inject
 * all properties into a {@link ComponentContext} as {@code StringKey<String>} entries.
 * Contracts then read them via {@code Lookup} typed accessors
 * (e.g., {@code lookup.getInt("list.defaultPageSize", 10)}).</p>
 */
public final class Config {
    private final Map<String, String> properties;

    /**
     * Creates an empty Config.
     */
    public Config() {
        this(Map.of());
    }

    private Config(final Map<String, String> properties) {
        this.properties = properties;
    }

    /**
     * Merges properties from a {@link Properties} source.
     * New entries override existing ones with the same key.
     *
     * @param source the properties to merge
     * @return a new Config with merged properties
     */
    public Config with(final Properties source) {
        Objects.requireNonNull(source, "source");
        final Map<String, String> merged = new LinkedHashMap<>(this.properties);
        for (final String key : source.stringPropertyNames()) {
            merged.put(key, source.getProperty(key));
        }
        return new Config(merged);
    }

    /**
     * Merges properties from a {@code Map<String, String>} source.
     * New entries override existing ones with the same key.
     *
     * @param source the map to merge
     * @return a new Config with merged properties
     */
    public Config with(final Map<String, String> source) {
        Objects.requireNonNull(source, "source");
        final Map<String, String> merged = new LinkedHashMap<>(this.properties);
        merged.putAll(source);
        return new Config(merged);
    }

    /**
     * Merges all properties from another Config.
     * The other Config's entries override this Config's entries for the same key.
     *
     * @param other the Config to merge from
     * @return a new Config with merged properties
     */
    public Config with(final Config other) {
        Objects.requireNonNull(other, "other");
        final Map<String, String> merged = new LinkedHashMap<>(this.properties);
        merged.putAll(other.properties);
        return new Config(merged);
    }

    /**
     * Adds or overrides a single property.
     *
     * @param key the property key
     * @param value the property value
     * @return a new Config with the property set
     */
    public Config with(final String key, final String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        final Map<String, String> merged = new LinkedHashMap<>(this.properties);
        merged.put(key, value);
        return new Config(merged);
    }

    // ===== Typed accessors =====

    /**
     * Gets a property value as a String.
     *
     * @param key the property key
     * @return the value, or null if not present
     */
    public String get(final String key) {
        return properties.get(key);
    }

    /**
     * Gets a property value as a String with a default.
     *
     * @param key the property key
     * @param defaultValue fallback if key is not present
     * @return the value, or defaultValue if not present
     */
    public String get(final String key, final String defaultValue) {
        final String value = properties.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets a property value parsed as an int.
     * Returns the default if the key is absent or not parseable.
     *
     * @param key the property key
     * @param defaultValue fallback if key is not present or not parseable
     * @return the parsed int value, or defaultValue
     */
    public int getInt(final String key, final int defaultValue) {
        final String value = properties.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a required property value parsed as an int.
     * Throws if the key is absent or the value is not a valid integer.
     *
     * @param key the property key
     * @return the parsed int value
     * @throws IllegalArgumentException if the key is absent or the value is not a valid integer
     */
    public int getRequiredInt(final String key) {
        final String value = properties.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Required config property '" + key + "' is not set");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Config property '" + key + "' has invalid integer value: '" + value + "'", e);
        }
    }

    /**
     * Gets a property value parsed as a boolean.
     * Returns the default if the key is absent or not parseable.
     *
     * @param key the property key
     * @param defaultValue fallback if key is not present or not parseable
     * @return the parsed boolean value, or defaultValue
     */
    public boolean getBoolean(final String key, final boolean defaultValue) {
        final String value = properties.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    /**
     * Gets a required property value parsed as a boolean.
     * Throws if the key is absent or the value is not "true"/"false".
     *
     * @param key the property key
     * @return the parsed boolean value
     * @throws IllegalArgumentException if the key is absent or the value is not "true" or "false"
     */
    public boolean getRequiredBoolean(final String key) {
        final String value = properties.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Required config property '" + key + "' is not set");
        }
        final String trimmed = value.trim().toLowerCase();
        if ("true".equals(trimmed)) {
            return true;
        }
        if ("false".equals(trimmed)) {
            return false;
        }
        throw new IllegalArgumentException(
                "Config property '" + key + "' has invalid boolean value: '" + value + "' (expected 'true' or 'false')");
    }

    /**
     * Returns an unmodifiable view of all properties.
     *
     * @return unmodifiable map of all config properties
     */
    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(properties);
    }

    // ===== Context integration =====

    /**
     * Injects all config properties into a {@link ComponentContext} as {@code StringKey<String>} entries.
     * Each property key becomes a context key in the same dot-separated namespace.
     *
     * @param context the context to enrich
     * @return a new ComponentContext with all config properties added
     */
    public ComponentContext applyTo(ComponentContext context) {
        Objects.requireNonNull(context, "context");
        for (final Map.Entry<String, String> entry : properties.entrySet()) {
            context = context.with(
                    new ContextKey.StringKey<>(entry.getKey(), String.class),
                    entry.getValue()
            );
        }
        return context;
    }
}
