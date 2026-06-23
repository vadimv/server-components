package rsp.compositions.application;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.component.ContextKey;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTests {

    @Nested
    class MergeSemanticsTests {

        @Test
        void empty_config_has_no_properties() {
            final Config config = new Config();
            assertNull(config.get("any.key"));
            assertTrue(config.asMap().isEmpty());
        }

        @Test
        void with_properties_adds_entries() {
            final Properties props = new Properties();
            props.setProperty("app.name", "test");
            props.setProperty("app.port", "8080");

            final Config config = new Config().with(props);

            assertEquals("test", config.get("app.name"));
            assertEquals("8080", config.get("app.port"));
        }

        @Test
        void later_with_overrides_earlier() {
            final Properties general = new Properties();
            general.setProperty("app.port", "8080");
            general.setProperty("app.name", "general");

            final Properties specific = new Properties();
            specific.setProperty("app.port", "9090");

            final Config config = new Config()
                    .with(general)
                    .with(specific);

            assertEquals("9090", config.get("app.port"));
            assertEquals("general", config.get("app.name"));
        }

        @Test
        void three_layer_merge_last_wins() {
            final Properties base = new Properties();
            base.setProperty("db.host", "localhost");
            base.setProperty("db.port", "5432");
            base.setProperty("app.debug", "false");

            final Properties host = new Properties();
            host.setProperty("db.host", "db.prod.internal");
            host.setProperty("app.debug", "false");

            final Properties system = new Properties();
            system.setProperty("app.debug", "true");

            final Config config = new Config()
                    .with(base)
                    .with(host)
                    .with(system);

            assertEquals("db.prod.internal", config.get("db.host"));
            assertEquals("5432", config.get("db.port"));
            assertEquals("true", config.get("app.debug"));
        }

        @Test
        void with_map_merges() {
            final Config config = new Config()
                    .with(Map.of("a", "1", "b", "2"))
                    .with(Map.of("b", "3", "c", "4"));

            assertEquals("1", config.get("a"));
            assertEquals("3", config.get("b"));
            assertEquals("4", config.get("c"));
        }

        @Test
        void with_config_merges() {
            final Config base = new Config().with("key", "base");
            final Config override = new Config().with("key", "override");

            final Config merged = base.with(override);

            assertEquals("override", merged.get("key"));
        }

        @Test
        void with_single_entry() {
            final Config config = new Config()
                    .with("a", "1")
                    .with("b", "2");

            assertEquals("1", config.get("a"));
            assertEquals("2", config.get("b"));
        }

        @Test
        void original_config_is_unchanged_after_with() {
            final Config original = new Config().with("key", "original");
            final Config modified = original.with("key", "modified");

            assertEquals("original", original.get("key"));
            assertEquals("modified", modified.get("key"));
        }
    }

    @Nested
    class TypedAccessorTests {

        @Test
        void get_with_default_returns_value_when_present() {
            final Config config = new Config().with("key", "value");
            assertEquals("value", config.get("key", "default"));
        }

        @Test
        void get_with_default_returns_default_when_absent() {
            final Config config = new Config();
            assertEquals("default", config.get("missing", "default"));
        }

        @Test
        void getInt_parses_integer() {
            final Config config = new Config().with("port", "8080");
            assertEquals(8080, config.getInt("port", 0));
        }

        @Test
        void getInt_returns_default_when_absent() {
            final Config config = new Config();
            assertEquals(10, config.getInt("missing", 10));
        }

        @Test
        void getInt_returns_default_for_non_numeric() {
            final Config config = new Config().with("bad", "not-a-number");
            assertEquals(42, config.getInt("bad", 42));
        }

        @Test
        void getBoolean_returns_default_for_non_parseable() {
            final Config config = new Config().with("flag", "yes");
            assertFalse(config.getBoolean("flag", false));
        }

        // ===== Required variants =====

        @Test
        void getRequiredInt_parses_integer() {
            final Config config = new Config().with("port", "8080");
            assertEquals(8080, config.getRequiredInt("port"));
        }

        @Test
        void getRequiredInt_throws_when_absent() {
            final Config config = new Config();
            final var ex = assertThrows(IllegalArgumentException.class,
                    () -> config.getRequiredInt("missing.key"));
            assertTrue(ex.getMessage().contains("missing.key"));
            assertTrue(ex.getMessage().contains("not set"));
        }

        @Test
        void getRequiredInt_throws_for_non_numeric() {
            final Config config = new Config().with("bad", "not-a-number");
            final var ex = assertThrows(IllegalArgumentException.class,
                    () -> config.getRequiredInt("bad"));
            assertTrue(ex.getMessage().contains("bad"));
            assertTrue(ex.getMessage().contains("not-a-number"));
        }

        @Test
        void getRequiredBoolean_parses_true() {
            final Config config = new Config().with("flag", "true");
            assertTrue(config.getRequiredBoolean("flag"));
        }

        @Test
        void getRequiredBoolean_throws_when_absent() {
            final Config config = new Config();
            final var ex = assertThrows(IllegalArgumentException.class,
                    () -> config.getRequiredBoolean("missing.key"));
            assertTrue(ex.getMessage().contains("missing.key"));
        }

        @Test
        void getRequiredBoolean_throws_for_invalid_value() {
            final Config config = new Config().with("flag", "yes");
            final var ex = assertThrows(IllegalArgumentException.class,
                    () -> config.getRequiredBoolean("flag"));
            assertTrue(ex.getMessage().contains("flag"));
            assertTrue(ex.getMessage().contains("yes"));
        }

        @Test
        void getInt_trims_whitespace() {
            final Config config = new Config().with("port", " 8080 ");
            assertEquals(8080, config.getInt("port", 0));
        }

        @Test
        void getBoolean_parses_true() {
            final Config config = new Config().with("debug", "true");
            assertTrue(config.getBoolean("debug", false));
        }

        @Test
        void getBoolean_parses_false() {
            final Config config = new Config().with("debug", "false");
            assertFalse(config.getBoolean("debug", true));
        }

        @Test
        void getBoolean_returns_default_when_absent() {
            final Config config = new Config();
            assertTrue(config.getBoolean("missing", true));
            assertFalse(config.getBoolean("missing", false));
        }

        @Test
        void getBoolean_trims_whitespace() {
            final Config config = new Config().with("debug", " true ");
            assertTrue(config.getBoolean("debug", false));
        }
    }

    @Nested
    class ContextIntegrationTests {

        @Test
        void applyTo_injects_all_properties_as_string_keys() {
            final Config config = new Config()
                    .with("list.defaultPageSize", "25")
                    .with("app.debug", "true");

            final ComponentContext context = config.applyTo(new ComponentContext());

            assertEquals("25", context.get(new ContextKey.StringKey<>("list.defaultPageSize", String.class)));
            assertEquals("true", context.get(new ContextKey.StringKey<>("app.debug", String.class)));
        }

        @Test
        void applyTo_preserves_existing_context_data() {
            final ComponentContext original = new ComponentContext()
                    .with(String.class, "existing-service");

            final Config config = new Config().with("new.key", "new-value");
            final ComponentContext enriched = config.applyTo(original);

            assertEquals("existing-service", enriched.get(String.class));
            assertEquals("new-value", enriched.get(new ContextKey.StringKey<>("new.key", String.class)));
        }

        @Test
        void applyTo_empty_config_returns_unchanged_context() {
            final ComponentContext context = new ComponentContext()
                    .with(String.class, "service");

            final ComponentContext result = new Config().applyTo(context);

            assertEquals("service", result.get(String.class));
        }

        @Test
        void lookup_typed_accessors_read_config_values() {
            final TestLookup lookup = new TestLookup()
                    .withData(new ContextKey.StringKey<>("list.defaultPageSize", String.class), "25")
                    .withData(new ContextKey.StringKey<>("app.debug", String.class), "true");

            assertEquals(25, lookup.getInt("list.defaultPageSize", 10));
            assertTrue(lookup.getBoolean("app.debug", false));
            assertEquals("25", lookup.getString("list.defaultPageSize"));
        }

        @Test
        void lookup_typed_accessors_return_defaults_when_missing() {
            final TestLookup lookup = new TestLookup();

            assertEquals(10, lookup.getInt("missing.key", 10));
            assertFalse(lookup.getBoolean("missing.key", false));
            assertNull(lookup.getString("missing.key"));
            assertEquals("fallback", lookup.getString("missing.key", "fallback"));
        }
    }

    @Nested
    class AsMapTests {

        @Test
        void asMap_returns_unmodifiable_view() {
            final Config config = new Config().with("key", "value");
            final Map<String, String> map = config.asMap();

            assertEquals("value", map.get("key"));
            assertThrows(UnsupportedOperationException.class, () -> map.put("new", "entry"));
        }
    }
}
