package rsp.application;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationConfigTests {
    @Test
    void layers_properties_maps_configs_and_single_values_without_mutating_sources() {
        Properties baseProperties = new Properties();
        baseProperties.setProperty("host", "localhost");
        baseProperties.setProperty("port", "8080");
        ApplicationConfig base = new ApplicationConfig().with(baseProperties);
        ApplicationConfig override = new ApplicationConfig().with(Map.of("port", "9090"));

        ApplicationConfig result = base.with(override).with("debug", "true");

        assertEquals("localhost", result.get("host"));
        assertEquals("9090", result.get("port"));
        assertEquals("true", result.get("debug"));
        assertEquals("8080", base.get("port"));
    }

    @Test
    void provides_default_and_strict_typed_accessors() {
        ApplicationConfig config = new ApplicationConfig()
                .with("port", " 8080 ")
                .with("enabled", " true ")
                .with("invalid", "value");

        assertEquals(8080, config.getInt("port", 1));
        assertEquals(7, config.getInt("missing", 7));
        assertEquals(7, config.getInt("invalid", 7));
        assertEquals(8080, config.getRequiredInt("port"));
        assertTrue(config.getBoolean("enabled", false));
        assertFalse(config.getBoolean("invalid", false));
        assertTrue(config.getRequiredBoolean("enabled"));
        assertEquals("fallback", config.get("missing", "fallback"));
        assertNull(config.get("missing"));
        assertThrows(IllegalArgumentException.class, () -> config.getRequiredInt("missing"));
        assertThrows(IllegalArgumentException.class, () -> config.getRequiredInt("invalid"));
        assertThrows(IllegalArgumentException.class, () -> config.getRequiredBoolean("invalid"));
    }

    @Test
    void exposes_an_unmodifiable_snapshot() {
        Map<String, String> values = new ApplicationConfig().with("key", "value").asMap();

        assertEquals(Map.of("key", "value"), values);
        assertThrows(UnsupportedOperationException.class, () -> values.put("other", "value"));
    }
}
