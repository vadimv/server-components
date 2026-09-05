package rsp.compositions.block;

import org.junit.jupiter.api.Test;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.Widget;
import rsp.compositions.ui.EditView;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FormContractTests {

    @Test
    void state_is_deeply_immutable_and_permits_nullable_optional_values() {
        DataSchema schema = DataSchema.builder().field("date", FieldType.DATE).build();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("date", null);
        List<String> messages = new java.util.ArrayList<>(List.of("Invalid date"));
        Map<String, List<String>> errors = new LinkedHashMap<>();
        errors.put("date", messages);

        EditView.EditViewState state = new EditView.EditViewState(values, schema, true, "/items",
                FormMode.CREATE, errors, "Create", FormCapabilities.create(), FormStatus.READY,
                "", false, "form-1");
        values.put("other", "ignored");
        messages.add("mutated");

        assertTrue(state.fieldValues().containsKey("date"));
        assertNull(state.fieldValues().get("date"));
        assertFalse(state.fieldValues().containsKey("other"));
        assertEquals(List.of("Invalid date"), state.errorsFor("date"));
        assertThrows(UnsupportedOperationException.class, () -> state.fieldValues().put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> state.errorsFor("date").add("x"));
    }

    @Test
    void codec_converts_types_without_silently_replacing_invalid_input() {
        DataSchema schema = DataSchema.builder()
                .field("count", FieldType.INTEGER)
                .field("date", FieldType.DATE)
                .field("enabled", FieldType.BOOLEAN)
                .field("primitiveCount", FieldType.INTEGER).javaType(int.class)
                .build();

        assertEquals(42, FormValueCodec.convert(schema.field("count"), "42").value());
        assertEquals(LocalDate.of(2026, 9, 1),
                FormValueCodec.convert(schema.field("date"), "2026-09-01").value());
        assertEquals(true, FormValueCodec.convert(schema.field("enabled"), "on").value());
        assertFalse(FormValueCodec.convert(schema.field("count"), "forty-two").valid());
        assertFalse(FormValueCodec.convert(schema.field("enabled"), "perhaps").valid());
        assertFalse(FormValueCodec.convert(schema.field("primitiveCount"), "").valid());
        assertNull(FormValueCodec.convert(schema.field("date"), "").value());
    }

    @Test
    void codec_uses_configured_defaults_and_enum_constants() {
        DataSchema schema = DataSchema.builder()
                .field("count", FieldType.INTEGER).defaultValue("7")
                .field("status", FieldType.ENUM).javaType(Status.class).widget(Widget.RADIO)
                .build();

        assertEquals(7, FormValueCodec.initialValue(schema.field("count")));
        FormValueCodec.Conversion conversion = FormValueCodec.convert(schema.field("status"), "open");
        assertTrue(conversion.valid());
        assertEquals(Status.OPEN, conversion.value());
    }

    @Test
    void mutation_result_defensively_copies_field_errors() {
        List<String> messages = new java.util.ArrayList<>(List.of("Required"));
        FormMutationResult result = FormMutationResult.invalid(Map.of("title", messages));
        messages.add("mutated");

        assertEquals(FormMutationResult.Status.INVALID, result.status());
        assertEquals(List.of("Required"), result.fieldErrors().get("title"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.fieldErrors().get("title").add("x"));
        assertThrows(IllegalArgumentException.class, () -> new FormMutationResult(
                FormMutationResult.Status.SUCCESS, Map.of("x", List.of("bad")), "", ""));
    }

    private enum Status {
        OPEN,
        CLOSED
    }
}
