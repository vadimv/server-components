package rsp.compositions.ui;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.component.TreeBuilder;
import rsp.component.definitions.LocalStateComponent;
import rsp.compositions.block.ListCapabilities;
import rsp.compositions.block.ListQuery;
import rsp.compositions.block.ListStatus;
import rsp.compositions.block.ListView;
import rsp.compositions.block.SortDirection;
import rsp.compositions.block.SortSpec;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.TextAlign;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DefaultListViewTests {
    private static final DataSchema SCHEMA = DataSchema.builder()
            .field("id", FieldType.ID).label("ID")
            .field("title", FieldType.STRING)
            .field("internal", FieldType.STRING)
            .column("title").sortable().filterable().width("40%").formatter(value -> "[" + value + "]")
            .column("id").sortable().align(TextAlign.RIGHT)
            .build()
            .withSelectable(true);

    @Test
    void renders_configured_columns_metadata_and_active_sort() {
        ListView.ListViewState state = state(List.of(Map.of("id", "1", "title", "Alpha", "internal", "secret")), 1);
        Document document = render(state);

        assertEquals(List.of("Title ↑", "ID", "Actions"), document.select("thead th").eachText().stream()
                .filter(text -> !text.isBlank()).toList());
        assertEquals(2, document.select(".grid-sort-button").size());
        assertEquals("ascending", document.select("th:has(.grid-sort-button:contains(Title))").attr("aria-sort"));
        assertFalse(document.select("th:has(.grid-sort-button:contains(ID))").hasAttr("aria-sort"));
        assertEquals("width: 40%;", document.select("th:has(.grid-sort-button:contains(Title))").attr("style"));
        assertEquals("[Alpha]", document.select("tbody tr td").get(1).text());
        assertFalse(document.text().contains("secret"));
    }

    @Test
    void empty_result_retains_headers_and_disables_forward_paging() {
        ListView.ListViewState state = state(List.of(), 0);
        Document document = render(state);

        assertEquals(2, document.select("thead .grid-sort-button").size());
        assertTrue(document.text().contains("No items to display."));
        assertTrue(document.select("button:contains(Next)").first().hasAttr("disabled"));
        assertTrue(document.text().contains("0 items"));
    }

    @Test
    void constrained_empty_result_has_distinct_message() {
        ListQuery query = new ListQuery(1, 10, new SortSpec("title", SortDirection.ASC), "missing", Map.of());
        ListView.ListViewState state = new ListView.ListViewState(List.of(), SCHEMA, query, 0, "/items",
                Set.of(), "Items", ListView.EditTarget.overlay(), ListCapabilities.crud(), "", false);

        assertTrue(render(state).text().contains("No items match the current search and filters."));
    }

    @Test
    void partial_selection_is_mixed_and_capabilities_hide_crud_actions() {
        ListView.ListViewState state = new ListView.ListViewState(
                List.of(Map.of("id", "1", "title", "One"), Map.of("id", "2", "title", "Two")),
                SCHEMA, new ListQuery(1, 10, new SortSpec("title", SortDirection.ASC), "", Map.of()),
                2, "/items", Set.of("1"), "Items", ListView.EditTarget.overlay(),
                new ListCapabilities("id", false, false, false), "", false);
        Document document = render(state);

        assertEquals("mixed", document.select("thead input[type=checkbox]").attr("aria-checked"));
        assertEquals(0, document.select(".create-button, .grid-row-actions, .btn-delete").size());
    }

    @Test
    void route_edit_link_carries_the_complete_query() {
        ListQuery query = new ListQuery(2, 25, new SortSpec("id", SortDirection.DESC),
                "hello world", Map.of("title", "draft"));
        ListView.ListViewState state = new ListView.ListViewState(
                List.of(Map.of("id", "7", "title", "Draft")), SCHEMA, query, 30, "/items",
                Set.of(), "Items", new ListView.EditTarget(true, false, "/items/{id}"),
                ListCapabilities.crud(), "", false);

        String href = render(state).selectFirst("a.edit-link").attr("href");
        String restored = URLDecoder.decode(href.substring(href.indexOf("fromQuery=") + 10),
                StandardCharsets.UTF_8);

        assertTrue(restored.contains("p=2"));
        assertTrue(restored.contains("size=25"));
        assertTrue(restored.contains("sort=id"));
        assertTrue(restored.contains("dir=desc"));
        assertTrue(restored.contains("q=hello+world"));
        assertTrue(restored.contains("filter.title=draft"));
    }

    @Test
    void renders_accessible_native_confirmation_dialogs() {
        ListView.ListViewState state = new ListView.ListViewState(
                List.of(Map.of("id", "7", "title", "Draft")), SCHEMA,
                new ListQuery(1, 10, new SortSpec("title", SortDirection.ASC), "", Map.of()),
                1, "/items", Set.of("7"), "Items", ListView.EditTarget.overlay(),
                ListCapabilities.crud(), "", false);
        Document document = render(state);

        assertEquals(2, document.select("dialog.confirmation-dialog[role=alertdialog]").size());
        Element bulk = document.selectFirst("dialog:has(.confirmation-dialog-title:contains(Delete selected items))");
        assertNotNull(bulk);
        assertNotNull(document.getElementById(bulk.attr("aria-labelledby")));
        assertNotNull(document.getElementById(bulk.attr("aria-describedby")));
        assertEquals("dialog", bulk.selectFirst("form").attr("method"));
        assertTrue(bulk.selectFirst(".confirmation-dialog-cancel").hasAttr("autofocus"));
    }

    @Test
    void deleting_state_marks_grid_busy_and_disables_mutating_controls() {
        ListView.ListViewState deleting = state(List.of(Map.of("id", "1", "title", "One")), 1)
                .withStatus(ListStatus.DELETING, "Deleting 1 item…");
        Document document = render(deleting);

        assertEquals("true", document.selectFirst(".data-grid").attr("aria-busy"));
        assertTrue(document.selectFirst(".create-button").hasAttr("disabled"));
        assertTrue(document.selectFirst(".grid-sort-button").hasAttr("disabled"));
        assertTrue(document.selectFirst(".grid-row-delete").hasAttr("disabled"));
        assertTrue(document.selectFirst("button.edit-button").hasAttr("disabled"));
        assertTrue(document.selectFirst(".grid-query input").hasAttr("disabled"));
    }

    @Test
    void related_list_column_renders_encoded_filter_links_and_respects_busy_state() {
        ListView.RelatedListColumn related = new ListView.RelatedListColumn(
                "comments", "Comments", "id", "/comments", "postId", "View comments");
        ListView.ListViewState state = new ListView.ListViewState(
                List.of(Map.of("id", "a b&c", "title", "One")), SCHEMA,
                new ListQuery(1, 10, new SortSpec("title", SortDirection.ASC), "", Map.of()),
                1, "/items", Set.of(), "Items", ListView.EditTarget.overlay(),
                ListCapabilities.crud(), "", false, ListStatus.READY, List.of(related));

        Document document = render(state);
        Element link = document.selectFirst("a.grid-related-link");

        assertEquals("Comments", document.selectFirst("th.grid-related-column").text());
        assertEquals("/comments?filter.postId=a+b%26c", link.attr("href"));
        assertEquals("View comments for row a b&c", link.attr("aria-label"));

        Document busy = render(state.withStatus(ListStatus.DELETING));
        Element disabled = busy.selectFirst("a.grid-related-link");
        assertFalse(disabled.hasAttr("href"));
        assertEquals("true", disabled.attr("aria-disabled"));

        ListView.ListViewState empty = new ListView.ListViewState(
                List.of(), SCHEMA, state.query(), 0, "/items", Set.of(), "Items",
                ListView.EditTarget.overlay(), ListCapabilities.crud(), "", false,
                ListStatus.READY, List.of(related));
        assertEquals("5", render(empty).selectFirst("td.grid-empty").attr("colspan"));
    }

    private static ListView.ListViewState state(List<Map<String, Object>> rows, long total) {
        return new ListView.ListViewState(rows, SCHEMA,
                new ListQuery(1, 10, new SortSpec("title", SortDirection.ASC), "", Map.of()),
                total, "/items", Set.of(), "Items", ListView.EditTarget.overlay(),
                ListCapabilities.crud(), "", false);
    }

    private static Document render(ListView.ListViewState state) {
        LocalStateComponent<ListView.ListViewState, ListView.ListIntent> component = new LocalStateComponent<>(
                (_, _) -> state, new DefaultListView(), (current, _) -> current);
        TreeBuilder treeBuilder = new TreeBuilder(
                new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"),
                new ComponentContext(),
                _ -> {});
        component.render(treeBuilder);
        return Jsoup.parseBodyFragment(treeBuilder.html());
    }
}
