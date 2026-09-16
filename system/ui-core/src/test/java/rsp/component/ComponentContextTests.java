package rsp.component;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ComponentContext type-safe key handling.
 */
public class ComponentContextTests {

    @Nested
    class TypeSafetyTests {

        @Test
        void with_valid_type_succeeds() {
            final ComponentContext context = new ComponentContext();
            final ContextKey.StringKey<String> key = new ContextKey.StringKey<>("test.key", String.class);

            final ComponentContext result = context.with(key, "hello");

            assertEquals("hello", result.get(key));
        }

        @Test
        void with_null_value_succeeds() {
            final ComponentContext context = new ComponentContext();
            final ContextKey.StringKey<String> key = new ContextKey.StringKey<>("test.key", String.class);

            final ComponentContext result = context.with(key, null);

            assertNull(result.get(key));
        }

        @Test
        void with_wrong_type_throws_exception() {
            final ComponentContext context = new ComponentContext();
            final ContextKey.StringKey<String> key = new ContextKey.StringKey<>("test.key", String.class);

            // Simulate type erasure bypass via raw types
            @SuppressWarnings({"unchecked", "rawtypes"})
            final ContextKey rawKey = key;

            final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.with(rawKey, 123)  // Integer instead of String
            );

            assertTrue(exception.getMessage().contains("Type mismatch"));
            assertTrue(exception.getMessage().contains("String"));
            assertTrue(exception.getMessage().contains("Integer"));
        }

        @Test
        void with_class_key_valid_type_succeeds() {
            final ComponentContext context = new ComponentContext();

            final ComponentContext result = context.with(String.class, "test");

            assertEquals("test", result.get(String.class));
        }

        @Test
        void with_class_key_wrong_type_throws_exception() {
            final ComponentContext context = new ComponentContext();
            final ContextKey.ClassKey<String> key = new ContextKey.ClassKey<>(String.class);

            @SuppressWarnings({"unchecked", "rawtypes"})
            final ContextKey rawKey = key;

            final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.with(rawKey, 456)
            );

            assertTrue(exception.getMessage().contains("Type mismatch"));
        }

        @Test
        void with_dynamic_key_valid_type_succeeds() {
            final ComponentContext context = new ComponentContext();
            final ContextKey.DynamicKey<String> baseKey = new ContextKey.DynamicKey<>("url.query", String.class);
            final ContextKey<String> pageKey = baseKey.with("page");

            final ComponentContext result = context.with(pageKey, "3");

            assertEquals("3", result.get(pageKey));
        }

        @Test
        void with_dynamic_key_wrong_type_throws_exception() {
            final ComponentContext context = new ComponentContext();
            final ContextKey.DynamicKey<String> baseKey = new ContextKey.DynamicKey<>("url.query", String.class);
            final ContextKey<String> pageKey = baseKey.with("page");

            @SuppressWarnings({"unchecked", "rawtypes"})
            final ContextKey rawKey = pageKey;

            final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.with(rawKey, 789)
            );

            assertTrue(exception.getMessage().contains("Type mismatch"));
        }
    }

    @Nested
    class KeyCollisionTests {

        @Test
        void class_key_and_string_key_same_name_no_collision() {
            ComponentContext context = new ComponentContext();

            // ClassKey with String.class
            context = context.with(String.class, "class-based");

            // StringKey with "java.lang.String" - should NOT collide
            final ContextKey.StringKey<String> stringKey = new ContextKey.StringKey<>("java.lang.String", String.class);
            context = context.with(stringKey, "string-based");

            // Both should be retrievable independently
            assertEquals("class-based", context.get(String.class));
            assertEquals("string-based", context.get(stringKey));
        }

        @Test
        void dynamic_keys_different_extensions_no_collision() {
            ComponentContext context = new ComponentContext();
            final ContextKey.DynamicKey<String> base = new ContextKey.DynamicKey<>("url.query", String.class);

            context = context
                .with(base.with("page"), "1")
                .with(base.with("sort"), "asc");

            assertEquals("1", context.get(base.with("page")));
            assertEquals("asc", context.get(base.with("sort")));
        }
    }

    @Nested
    class ImmutabilityTests {

        @Test
        void with_returns_new_instance() {
            final ComponentContext original = new ComponentContext();
            final ContextKey.StringKey<String> key = new ContextKey.StringKey<>("test", String.class);

            final ComponentContext modified = original.with(key, "value");

            assertNotSame(original, modified);
            assertNull(original.get(key));  // Original unchanged
            assertEquals("value", modified.get(key));
        }
    }
}
