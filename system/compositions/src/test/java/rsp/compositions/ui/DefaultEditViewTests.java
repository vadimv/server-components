package rsp.compositions.ui;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.component.TreeBuilder;
import rsp.component.definitions.LocalStateComponent;
import rsp.compositions.block.FormCapabilities;
import rsp.compositions.block.FormMode;
import rsp.compositions.block.FormStatus;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.Widget;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultEditViewTests {
    private static final DataSchema SCHEMA = DataSchema.builder()
            .field("id", FieldType.ID)
            .field("title", FieldType.STRING).required().maxLength(20).placeholder("A title")
            .field("notes", FieldType.TEXT).widget(Widget.TEXTAREA)
            .field("status", FieldType.ENUM).javaType(Status.class).widget(Widget.RADIO)
            .field("category", FieldType.ENUM).options("News", "Guide")
            .field("enabled", FieldType.BOOLEAN)
            .field("locked", FieldType.STRING).readOnly()
            .build();

    @Test
    void renders_semantic_form_complete_widgets_and_unique_field_ids() {
        Document document = render(state(Map.of(
                "id", "7", "title", "Hello", "notes", "Long text", "status", Status.OPEN,
                "category", "Guide", "enabled", true, "locked", "server")));

        assertEquals("edit", document.selectFirst(".data-form").attr("data-form-mode"));
        assertNotNull(document.selectFirst("form[aria-labelledby]"));
        assertNotNull(document.selectFirst("button.save-button[type=submit]"));
        assertNull(document.selectFirst("[name=id]"), "hidden/protected fields must not be submitted");

        Element title = document.selectFirst("[name=title]");
        assertNotNull(title);
        assertTrue(title.id().startsWith("form-test-"));
        assertEquals(title.id(), document.selectFirst("label:contains(Title)").attr("for"));
        assertTrue(title.hasAttr("required"));
        assertEquals("20", title.attr("maxlength"));
        assertEquals("A title", title.attr("placeholder"));
        assertEquals("8", document.selectFirst("textarea[name=notes]").attr("rows"));
        assertEquals(2, document.select("input[type=radio][name=status]").size());
        assertEquals("OPEN", document.selectFirst("input[type=radio][name=status][checked]").attr("value"));
        assertEquals("Guide", document.selectFirst("select[name=category] option[selected]").attr("value"));
        assertTrue(document.selectFirst("input[name=enabled]").hasAttr("checked"));
        assertTrue(document.selectFirst("input[name=locked]").hasAttr("readonly"));
        assertNotNull(document.selectFirst("button.btn-delete"));
    }

    @Test
    void associates_errors_with_fields_and_renders_alert_summary() {
        EditView.EditViewState state = new EditView.EditViewState(
                values(""), SCHEMA, true, "/items", FormMode.EDIT,
                Map.of("title", List.of("Title is required"), "status", List.of("Status is required")),
                "Edit", FormCapabilities.edit(),
                FormStatus.READY, "Please correct the highlighted fields.", true, "form-test");
        Document document = render(state);
        Element title = document.selectFirst("[name=title]");

        assertEquals("true", title.attr("aria-invalid"));
        String errorId = title.attr("aria-describedby");
        assertFalse(errorId.isBlank());
        assertNotNull(document.getElementById(errorId));
        Element titleSummaryLink = document.selectFirst(".form-error-summary a:contains(Title is required)");
        assertEquals("#" + title.id(), titleSummaryLink.attr("href"));
        Element radioSummaryLink = document.selectFirst(".form-error-summary a:contains(Status is required)");
        assertNotNull(document.selectFirst(radioSummaryLink.attr("href")));
        assertEquals("alert", document.selectFirst(".form-error-summary").attr("role"));
        assertEquals("alert", document.selectFirst(".form-message-error").attr("role"));
    }

    @Test
    void capabilities_and_status_gate_actions_and_unavailable_forms() {
        EditView.EditViewState readOnly = new EditView.EditViewState(values("Read only"), SCHEMA, false,
                "/items", FormMode.EDIT, Map.of(), "View", new FormCapabilities(false, false, true),
                FormStatus.READY, "", false, "form-test");
        Document readOnlyDocument = render(readOnly);
        assertEquals(0, readOnlyDocument.select(".save-button, .btn-delete").size());
        assertEquals(1, readOnlyDocument.select(".cancel-button").size());

        EditView.EditViewState missing = new EditView.EditViewState(values(""), SCHEMA, false,
                "/items", FormMode.EDIT, Map.of(), "Edit", new FormCapabilities(false, false, true),
                FormStatus.NOT_FOUND, "The requested item was not found.", true, "form-test");
        Document missingDocument = render(missing);
        assertEquals(0, missingDocument.select("form").size());
        assertEquals(1, missingDocument.select(".cancel-button").size());
        assertEquals("alert", missingDocument.selectFirst(".form-message").attr("role"));
    }

    @Test
    void busy_state_disables_every_available_action() {
        EditView.EditViewState busy = new EditView.EditViewState(values("Saving"), SCHEMA, true,
                "/items", FormMode.EDIT, Map.of(), "Edit", FormCapabilities.edit(),
                FormStatus.SUBMITTING, "Saving…", false, "form-test");
        Document document = render(busy);

        assertEquals("true", document.selectFirst(".data-form").attr("aria-busy"));
        assertTrue(document.selectFirst(".save-button").hasAttr("disabled"));
        assertTrue(document.selectFirst(".cancel-button").hasAttr("disabled"));
        assertTrue(document.selectFirst(".btn-delete").hasAttr("disabled"));
    }

    @Test
    void renders_delete_and_dirty_cancel_as_accessible_native_dialogs() {
        EditView.EditViewState dirty = new EditView.EditViewState(values("Changed"), SCHEMA, true,
                "/items", FormMode.EDIT, Map.of(), "Edit", FormCapabilities.edit(),
                FormStatus.READY, "", false, "form-test");
        Document document = render(dirty);

        assertEquals(2, document.select("dialog.confirmation-dialog[role=alertdialog]").size());
        Element discard = document.selectFirst(
                "dialog:has(.confirmation-dialog-title:contains(Discard unsaved changes))");
        assertNotNull(discard);
        assertNotNull(document.getElementById(discard.attr("aria-labelledby")));
        assertNotNull(document.getElementById(discard.attr("aria-describedby")));
        assertEquals("dialog", discard.selectFirst("form").attr("method"));
        assertTrue(discard.selectFirst(".confirmation-dialog-cancel").hasAttr("autofocus"));
        assertEquals(1, document.select(".data-form > form").size(),
                "confirmation forms must not be nested inside the edit form");
    }

    private static EditView.EditViewState state(Map<String, Object> values) {
        return new EditView.EditViewState(values, SCHEMA, false, "/items", FormMode.EDIT, Map.of(),
                "Edit", FormCapabilities.edit(), FormStatus.READY, "", false, "form-test");
    }

    private static Map<String, Object> values(String title) {
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("id", "7");
        values.put("title", title);
        values.put("notes", "");
        values.put("status", Status.OPEN);
        values.put("category", "News");
        values.put("enabled", false);
        values.put("locked", "server");
        return values;
    }

    private static Document render(EditView.EditViewState state) {
        LocalStateComponent<EditView.EditViewState, EditView.EditIntent> component = new LocalStateComponent<>(
                (_, _) -> state, new DefaultFormView(), (current, _) -> current);
        TreeBuilder treeBuilder = new TreeBuilder(new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"), new ComponentContext(), _ -> { });
        component.render(treeBuilder);
        assertTrue(treeBuilder.exceptions().isEmpty(), () -> treeBuilder.exceptions().toString());
        return Jsoup.parseBodyFragment(treeBuilder.html());
    }

    private enum Status {
        OPEN,
        CLOSED
    }
}
