package rsp.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Immutable string configuration with last-writer-wins layering. */
public final class ApplicationConfig {
    public static final ApplicationConfig EMPTY = new ApplicationConfig(Map.of());

    private final Map<String, String> properties;

    public ApplicationConfig() {
        this(Map.of());
    }

    private ApplicationConfig(Map<String, String> properties) {
        this.properties = Map.copyOf(properties);
    }

    public ApplicationConfig with(Properties source) {
        Objects.requireNonNull(source, "source");
        Map<String, String> additions = new LinkedHashMap<>();
        for (String key : source.stringPropertyNames()) {
            additions.put(key, source.getProperty(key));
        }
        return with(additions);
    }

    public ApplicationConfig with(Map<String, String> source) {
        Objects.requireNonNull(source, "source");
        Map<String, String> merged = new LinkedHashMap<>(properties);
        source.forEach((key, value) -> merged.put(
                Objects.requireNonNull(key, "configuration key"),
                Objects.requireNonNull(value, "configuration value")));
        return new ApplicationConfig(merged);
    }

    public ApplicationConfig with(ApplicationConfig other) {
        return with(Objects.requireNonNull(other, "other").properties);
    }

    public ApplicationConfig with(String key, String value) {
        return with(Map.of(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value")));
    }

    public String get(String key) {
        return properties.get(Objects.requireNonNull(key, "key"));
    }

    public String get(String key, String defaultValue) {
        return properties.getOrDefault(Objects.requireNonNull(key, "key"), defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public int getRequiredInt(String key) {
        String value = required(key);
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(
                    "Configuration property '" + key + "' has invalid integer value: '" + value + "'", failure);
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    public boolean getRequiredBoolean(String key) {
        String value = required(key).trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(
                    "Configuration property '" + key + "' has invalid boolean value: '" + value + "'");
        };
    }

    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(properties);
    }

    private String required(String key) {
        String value = get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required configuration property '" + key + "' is not set");
        }
        return value;
    }
}
