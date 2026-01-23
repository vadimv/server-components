package rsp.compositions.schema;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.compositions.DataSchema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Schema DSL infrastructure.
 */
public class SchemaDslTests {

    @Nested
    class FieldTypeTests {

        @Test
        void fromJavaType_boolean_returns_BOOLEAN() {
            assertEquals(FieldType.BOOLEAN, FieldType.fromJavaType(Boolean.class));
            assertEquals(FieldType.BOOLEAN, FieldType.fromJavaType(boolean.class));
        }

        @Test
        void fromJavaType_integer_returns_INTEGER() {
            assertEquals(FieldType.INTEGER, FieldType.fromJavaType(Integer.class));
            assertEquals(FieldType.INTEGER, FieldType.fromJavaType(int.class));
            assertEquals(FieldType.INTEGER, FieldType.fromJavaType(Long.class));
            assertEquals(FieldType.INTEGER, FieldType.fromJavaType(long.class));
        }

        @Test
        void fromJavaType_decimal_returns_DECIMAL() {
            assertEquals(FieldType.DECIMAL, FieldType.fromJavaType(Double.class));
            assertEquals(FieldType.DECIMAL, FieldType.fromJavaType(double.class));
            assertEquals(FieldType.DECIMAL, FieldType.fromJavaType(Float.class));
            assertEquals(FieldType.DECIMAL, FieldType.fromJavaType(float.class));
        }

        @Test
        void fromJavaType_date_returns_DATE() {
            assertEquals(FieldType.DATE, FieldType.fromJavaType(LocalDate.class));
        }

        @Test
        void fromJavaType_datetime_returns_DATETIME() {
            assertEquals(FieldType.DATETIME, FieldType.fromJavaType(LocalDateTime.class));
        }

        @Test
        void fromJavaType_enum_returns_ENUM() {
            assertEquals(FieldType.ENUM, FieldType.fromJavaType(Widget.class));
        }

        @Test
        void fromJavaType_string_returns_STRING() {
            assertEquals(FieldType.STRING, FieldType.fromJavaType(String.class));
        }

        @Test
        void defaultJavaType_returns_correct_types() {
            assertEquals(String.class, FieldType.ID.defaultJavaType());
            assertEquals(String.class, FieldType.STRING.defaultJavaType());
            assertEquals(String.class, FieldType.TEXT.defaultJavaType());
            assertEquals(Integer.class, FieldType.INTEGER.defaultJavaType());
            assertEquals(Double.class, FieldType.DECIMAL.defaultJavaType());
            assertEquals(Boolean.class, FieldType.BOOLEAN.defaultJavaType());
            assertEquals(LocalDate.class, FieldType.DATE.defaultJavaType());
            assertEquals(LocalDateTime.class, FieldType.DATETIME.defaultJavaType());
        }
    }

    @Nested
    class WidgetTests {

        @Test
        void fromFieldType_returns_correct_defaults() {
            assertEquals(Widget.HIDDEN, Widget.fromFieldType(FieldType.ID));
            assertEquals(Widget.TEXT, Widget.fromFieldType(FieldType.STRING));
            assertEquals(Widget.TEXTAREA, Widget.fromFieldType(FieldType.TEXT));
            assertEquals(Widget.NUMBER, Widget.fromFieldType(FieldType.INTEGER));
            assertEquals(Widget.NUMBER, Widget.fromFieldType(FieldType.DECIMAL));
            assertEquals(Widget.CHECKBOX, Widget.fromFieldType(FieldType.BOOLEAN));
            assertEquals(Widget.DATE_PICKER, Widget.fromFieldType(FieldType.DATE));
            assertEquals(Widget.DATE_PICKER, Widget.fromFieldType(FieldType.DATETIME));
            assertEquals(Widget.SELECT, Widget.fromFieldType(FieldType.ENUM));
        }
    }

    @Nested
    class ValidatorsTests {

        @Test
        void required_fails_for_null() {
            Validator v = Validators.required();
            ValidationResult result = v.validate("name", null);
            assertFalse(result.isValid());
            assertFalse(result.errorsFor("name").isEmpty());
        }

        @Test
        void required_fails_for_blank_string() {
            Validator v = Validators.required();
            ValidationResult result = v.validate("name", "   ");
            assertFalse(result.isValid());
        }

        @Test
        void required_passes_for_non_empty_value() {
            Validator v = Validators.required();
            ValidationResult result = v.validate("name", "test");
            assertTrue(result.isValid());
        }

        @Test
        void required_provides_html_attribute() {
            Validator v = Validators.required();
            assertEquals("required", v.htmlAttributes().get("required"));
        }

        @Test
        void maxLength_fails_when_exceeded() {
            Validator v = Validators.maxLength(5);
            ValidationResult result = v.validate("name", "123456");
            assertFalse(result.isValid());
        }

        @Test
        void maxLength_passes_when_within_limit() {
            Validator v = Validators.maxLength(5);
            ValidationResult result = v.validate("name", "12345");
            assertTrue(result.isValid());
        }

        @Test
        void maxLength_provides_html_attribute() {
            Validator v = Validators.maxLength(100);
            assertEquals("100", v.htmlAttributes().get("maxlength"));
        }

        @Test
        void minLength_fails_when_too_short() {
            Validator v = Validators.minLength(3);
            ValidationResult result = v.validate("name", "ab");
            assertFalse(result.isValid());
        }

        @Test
        void minLength_passes_when_long_enough() {
            Validator v = Validators.minLength(3);
            ValidationResult result = v.validate("name", "abc");
            assertTrue(result.isValid());
        }

        @Test
        void pattern_fails_when_not_matching() {
            Validator v = Validators.pattern("[0-9]+");
            ValidationResult result = v.validate("code", "abc");
            assertFalse(result.isValid());
        }

        @Test
        void pattern_passes_when_matching() {
            Validator v = Validators.pattern("[0-9]+");
            ValidationResult result = v.validate("code", "123");
            assertTrue(result.isValid());
        }

        @Test
        void email_fails_for_invalid_email() {
            Validator v = Validators.email();
            ValidationResult result = v.validate("email", "notanemail");
            assertFalse(result.isValid());
        }

        @Test
        void email_passes_for_valid_email() {
            Validator v = Validators.email();
            ValidationResult result = v.validate("email", "test@example.com");
            assertTrue(result.isValid());
        }

        @Test
        void range_fails_when_out_of_bounds() {
            Validator v = Validators.range(1, 10);
            assertFalse(v.validate("age", 0).isValid());
            assertFalse(v.validate("age", 11).isValid());
        }

        @Test
        void range_passes_when_in_bounds() {
            Validator v = Validators.range(1, 10);
            assertTrue(v.validate("age", 1).isValid());
            assertTrue(v.validate("age", 10).isValid());
            assertTrue(v.validate("age", 5).isValid());
        }
    }

    @Nested
    class ValidationResultTests {

        @Test
        void success_is_valid() {
            ValidationResult result = ValidationResult.success();
            assertTrue(result.isValid());
            assertTrue(result.errors().isEmpty());
        }

        @Test
        void failure_is_not_valid() {
            ValidationResult result = ValidationResult.failure("field", "error message");
            assertFalse(result.isValid());
            assertEquals(1, result.errorsFor("field").size());
        }

        @Test
        void merge_combines_errors() {
            ValidationResult r1 = ValidationResult.failure("f1", "e1");
            ValidationResult r2 = ValidationResult.failure("f2", "e2");
            ValidationResult merged = r1.merge(r2);

            assertFalse(merged.isValid());
            assertEquals(1, merged.errorsFor("f1").size());
            assertEquals(1, merged.errorsFor("f2").size());
        }

        @Test
        void merge_two_successes_is_success() {
            ValidationResult merged = ValidationResult.success().merge(ValidationResult.success());
            assertTrue(merged.isValid());
        }
    }

    @Nested
    class SchemaBuilderTests {

        @Test
        void builds_schema_with_single_field() {
            DataSchema schema = DataSchema.builder()
                .field("name", FieldType.STRING)
                .build();

            assertEquals(1, schema.fields().size());
            assertEquals("name", schema.fields().get(0).name());
            assertEquals(FieldType.STRING, schema.fields().get(0).fieldType());
        }

        @Test
        void builds_schema_with_multiple_fields() {
            DataSchema schema = DataSchema.builder()
                .field("id", FieldType.ID).hidden()
                .field("name", FieldType.STRING).required()
                .field("active", FieldType.BOOLEAN)
                .build();

            assertEquals(3, schema.fields().size());
            assertEquals("id", schema.fields().get(0).name());
            assertEquals("name", schema.fields().get(1).name());
            assertEquals("active", schema.fields().get(2).name());
        }

        @Test
        void field_builder_sets_label() {
            DataSchema schema = DataSchema.builder()
                .field("firstName", FieldType.STRING).label("First Name")
                .build();

            assertEquals("First Name", schema.fields().get(0).displayName());
        }

        @Test
        void field_builder_sets_required() {
            DataSchema schema = DataSchema.builder()
                .field("name", FieldType.STRING).required()
                .build();

            assertTrue(schema.fields().get(0).isRequired());
        }

        @Test
        void field_builder_sets_hidden() {
            DataSchema schema = DataSchema.builder()
                .field("id", FieldType.ID).hidden()
                .build();

            assertTrue(schema.fields().get(0).isHidden());
        }

        @Test
        void field_builder_sets_widget() {
            DataSchema schema = DataSchema.builder()
                .field("bio", FieldType.STRING).widget(Widget.TEXTAREA)
                .build();

            assertEquals(Widget.TEXTAREA, schema.fields().get(0).widget());
        }

        @Test
        void field_builder_sets_placeholder() {
            DataSchema schema = DataSchema.builder()
                .field("email", FieldType.STRING).placeholder("Enter email...")
                .build();

            assertEquals("Enter email...", schema.fields().get(0).options().placeholder());
        }

        @Test
        void field_builder_adds_validators() {
            DataSchema schema = DataSchema.builder()
                .field("name", FieldType.STRING).required().maxLength(50)
                .build();

            // required() and maxLength() each add a validator
            assertEquals(2, schema.fields().get(0).validators().size());
        }

        @Test
        void schema_validate_runs_all_validators() {
            DataSchema schema = DataSchema.builder()
                .field("name", FieldType.STRING).required()
                .build();

            ValidationResult result = schema.validate(Map.of("name", ""));
            assertFalse(result.isValid());
        }

        @Test
        void schema_validate_passes_when_valid() {
            DataSchema schema = DataSchema.builder()
                .field("name", FieldType.STRING).required()
                .build();

            ValidationResult result = schema.validate(Map.of("name", "John"));
            assertTrue(result.isValid());
        }
    }

    @Nested
    class FieldDefTests {

        @Test
        void fromColumnDef_converts_correctly() {
            DataSchema.ColumnDef column = new DataSchema.ColumnDef("active", Boolean.class);
            FieldDef field = FieldDef.fromColumnDef(column);

            assertEquals("active", field.name());
            assertEquals("Active", field.displayName());
            assertEquals(Boolean.class, field.type());
            assertEquals(FieldType.BOOLEAN, field.fieldType());
            assertEquals(Widget.CHECKBOX, field.widget());
        }

        @Test
        void toColumnDef_converts_back() {
            FieldDef field = new FieldDef(
                "name", "Full Name", String.class, FieldType.STRING,
                Widget.TEXT, null, null
            );
            DataSchema.ColumnDef column = field.toColumnDef();

            assertEquals("name", column.name());
            assertEquals("Full Name", column.displayName());
            assertEquals(String.class, column.type());
        }

        @Test
        void validate_runs_all_validators() {
            FieldDef field = new FieldDef(
                "email", "Email", String.class, FieldType.STRING,
                Widget.TEXT,
                java.util.List.of(Validators.required(), Validators.email()),
                null
            );

            ValidationResult result = field.validate("invalid");
            assertFalse(result.isValid());
        }

        @Test
        void htmlValidationAttributes_collects_from_validators() {
            FieldDef field = new FieldDef(
                "name", "Name", String.class, FieldType.STRING,
                Widget.TEXT,
                java.util.List.of(Validators.required(), Validators.maxLength(100)),
                null
            );

            Map<String, String> attrs = field.htmlValidationAttributes();
            assertEquals("required", attrs.get("required"));
            assertEquals("100", attrs.get("maxlength"));
        }
    }

    @Nested
    class BackwardCompatibilityTests {

        record TestRecord(String id, String name, int count) {}

        @Test
        void fromRecordClass_still_works() {
            DataSchema schema = DataSchema.fromRecordClass(TestRecord.class);

            assertEquals(3, schema.columns().size());
            assertEquals(3, schema.fields().size());
        }

        @Test
        void columns_and_fields_are_consistent() {
            DataSchema schema = DataSchema.fromRecordClass(TestRecord.class);

            assertEquals(schema.columns().size(), schema.fields().size());
            for (int i = 0; i < schema.columns().size(); i++) {
                assertEquals(schema.columns().get(i).name(), schema.fields().get(i).name());
            }
        }

        @Test
        void builder_schema_has_both_columns_and_fields() {
            DataSchema schema = DataSchema.builder()
                .field("name", FieldType.STRING)
                .build();

            assertEquals(1, schema.columns().size());
            assertEquals(1, schema.fields().size());
            assertEquals("name", schema.columns().get(0).name());
            assertEquals("name", schema.fields().get(0).name());
        }
    }

    @Nested
    class ColumnConfigTests {

        @Test
        void column_config_with_defaults() {
            ColumnConfig config = new ColumnConfig("title");

            assertEquals("title", config.fieldName());
            assertFalse(config.sortable());
            assertFalse(config.filterable());
            assertNull(config.width());
            assertEquals(TextAlign.LEFT, config.align());
            assertNull(config.formatter());
        }

        @Test
        void column_config_immutable_updates() {
            ColumnConfig config = new ColumnConfig("title")
                .withSortable()
                .withFilterable()
                .withWidth("40%")
                .withAlign(TextAlign.RIGHT);

            assertTrue(config.sortable());
            assertTrue(config.filterable());
            assertEquals("40%", config.width());
            assertEquals(TextAlign.RIGHT, config.align());
        }

        @Test
        void column_config_requires_field_name() {
            assertThrows(IllegalArgumentException.class, () -> new ColumnConfig(null));
            assertThrows(IllegalArgumentException.class, () -> new ColumnConfig(""));
            assertThrows(IllegalArgumentException.class, () -> new ColumnConfig("   "));
        }
    }

    @Nested
    class ColumnBuilderTests {

        @Test
        void builder_with_column_configuration() {
            DataSchema schema = DataSchema.builder()
                .field("title", FieldType.STRING).required()
                .field("status", FieldType.STRING)
                .column("title").sortable().filterable().width("50%")
                .column("status").sortable()
                .build();

            assertEquals(2, schema.fields().size());
            assertEquals(2, schema.columnConfigs().size());

            ColumnConfig titleConfig = schema.columnConfig("title");
            assertTrue(titleConfig.sortable());
            assertTrue(titleConfig.filterable());
            assertEquals("50%", titleConfig.width());

            ColumnConfig statusConfig = schema.columnConfig("status");
            assertTrue(statusConfig.sortable());
            assertFalse(statusConfig.filterable());
        }

        @Test
        void column_config_defaults_for_undefined_column() {
            DataSchema schema = DataSchema.builder()
                .field("title", FieldType.STRING)
                .build();

            ColumnConfig config = schema.columnConfig("title");
            assertEquals("title", config.fieldName());
            assertFalse(config.sortable());
            assertFalse(config.filterable());
        }

        @Test
        void column_with_align() {
            DataSchema schema = DataSchema.builder()
                .field("amount", FieldType.DECIMAL)
                .column("amount").align(TextAlign.RIGHT)
                .build();

            assertEquals(TextAlign.RIGHT, schema.columnConfig("amount").align());
        }

        @Test
        void transition_from_field_to_column_and_back() {
            DataSchema schema = DataSchema.builder()
                .field("id", FieldType.ID).hidden()
                .field("title", FieldType.STRING).required()
                .column("title").sortable()
                .field("status", FieldType.STRING)
                .build();

            assertEquals(3, schema.fields().size());
            assertEquals(1, schema.columnConfigs().size());
            assertTrue(schema.columnConfig("title").sortable());
        }
    }

    @Nested
    class ListColumnsTests {

        @Test
        void listColumns_returns_visible_fields_when_no_config() {
            DataSchema schema = DataSchema.builder()
                .field("id", FieldType.ID).hidden()
                .field("title", FieldType.STRING)
                .field("status", FieldType.STRING)
                .build();

            assertEquals(2, schema.listColumns().size());
            assertEquals("title", schema.listColumns().get(0).name());
            assertEquals("status", schema.listColumns().get(1).name());
        }

        @Test
        void listColumns_respects_explicit_column_order() {
            DataSchema schema = DataSchema.builder()
                .field("id", FieldType.ID).hidden()
                .field("title", FieldType.STRING)
                .field("status", FieldType.STRING)
                .field("createdAt", FieldType.DATETIME)
                .column("status").sortable()
                .column("title").sortable()
                .build();

            // Columns are returned in the order they were configured
            assertEquals(2, schema.listColumns().size());
            assertEquals("status", schema.listColumns().get(0).name());
            assertEquals("title", schema.listColumns().get(1).name());
        }

        @Test
        void listColumns_excludes_hidden_fields_from_config() {
            DataSchema schema = DataSchema.builder()
                .field("id", FieldType.ID).hidden()
                .field("title", FieldType.STRING)
                .column("id").sortable()  // Even if configured, hidden field is excluded
                .column("title").sortable()
                .build();

            assertEquals(1, schema.listColumns().size());
            assertEquals("title", schema.listColumns().get(0).name());
        }
    }

    @Nested
    class SelectableTests {

        @Test
        void schema_not_selectable_by_default() {
            DataSchema schema = DataSchema.builder()
                .field("id", FieldType.ID)
                .field("title", FieldType.STRING)
                .build();

            assertFalse(schema.selectable());
        }

        @Test
        void selectable_from_schema_builder() {
            DataSchema schema = DataSchema.builder()
                .field("id", FieldType.ID)
                .field("title", FieldType.STRING)
                .selectable()
                .build();

            assertTrue(schema.selectable());
        }

        @Test
        void selectable_from_field_builder() {
            DataSchema schema = DataSchema.builder()
                .field("id", FieldType.ID)
                .field("title", FieldType.STRING).required()
                .selectable()
                .build();

            assertTrue(schema.selectable());
            assertEquals(2, schema.fields().size());
        }

        @Test
        void selectable_from_column_builder() {
            DataSchema schema = DataSchema.builder()
                .field("id", FieldType.ID)
                .field("title", FieldType.STRING)
                .column("title").sortable()
                .selectable()
                .build();

            assertTrue(schema.selectable());
            assertTrue(schema.columnConfig("title").sortable());
        }

        @Test
        void selectable_with_additional_fields_after() {
            DataSchema schema = DataSchema.builder()
                .field("id", FieldType.ID)
                .selectable()
                .field("title", FieldType.STRING)
                .build();

            assertTrue(schema.selectable());
            assertEquals(2, schema.fields().size());
        }
    }
}
