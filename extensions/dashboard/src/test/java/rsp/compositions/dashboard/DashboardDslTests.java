package rsp.compositions.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DashboardDslTests {

    @Test
    void builds_layout_with_grid_settings_and_widget_placements() {
        DashboardDefinition definition = DashboardDsl.dashboard("ops", "Operations")
                .columns(8)
                .rowHeightPx(80)
                .gap("12px")
                .place(DashboardDsl.value("a")
                                .title("Temperature")
                                .value(TestDashboardRuntime.SIGNAL),
                        DashboardDsl.at(2, 3).span(4, 2))
                .build();

        assertEquals("ops", definition.id());
        assertEquals("Operations", definition.title());
        assertEquals(8, definition.grid().columns());
        assertEquals(80, definition.grid().rowHeightPx());
        assertEquals("12px", definition.grid().gap());
        assertEquals(1, definition.widgets().size());
        assertEquals("a", definition.widgets().getFirst().widget().id());
        assertEquals(new GridArea(2, 3, 4, 2), definition.widgets().getFirst().area());
    }

    @Test
    void rejects_duplicate_widget_ids() {
        assertThrows(IllegalArgumentException.class, () -> DashboardDsl.dashboard("test", "Test")
                .place(new TestWidgetDefinition("same"), DashboardDsl.at(1, 1).span(2, 1))
                .place(new TestWidgetDefinition("same"), DashboardDsl.at(3, 1).span(2, 1))
                .build());
    }

    @Test
    void rejects_invalid_grid_coordinates_and_spans() {
        assertThrows(IllegalArgumentException.class, () -> DashboardDsl.at(0, 1).span(1, 1));
        assertThrows(IllegalArgumentException.class, () -> DashboardDsl.at(1, 0).span(1, 1));
        assertThrows(IllegalArgumentException.class, () -> DashboardDsl.at(1, 1).span(0, 1));
        assertThrows(IllegalArgumentException.class, () -> DashboardDsl.at(1, 1).span(1, 0));
    }

    @Test
    void rejects_placements_extending_past_configured_columns() {
        assertThrows(IllegalArgumentException.class, () -> DashboardDsl.dashboard("test", "Test")
                .columns(4)
                .place(new TestWidgetDefinition("wide"), DashboardDsl.at(3, 1).span(3, 1))
                .build());
    }

    @Test
    void rejects_invalid_layout_settings() {
        assertThrows(IllegalArgumentException.class,
                () -> DashboardDsl.dashboard("test", "Test").columns(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> DashboardDsl.dashboard("test", "Test").rowHeightPx(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> DashboardDsl.dashboard("test", "Test").gap(" ").build());
    }

    @Test
    void rejects_null_widgets() {
        assertThrows(NullPointerException.class, () -> DashboardDsl.dashboard("test", "Test")
                .place((WidgetDefinition) null, DashboardDsl.at(1, 1).span(1, 1)));
    }

    @Test
    void rejects_overlapping_widgets() {
        assertThrows(IllegalArgumentException.class, () -> DashboardDsl.dashboard("test", "Test")
                .place(new TestWidgetDefinition("first"), DashboardDsl.at(1, 1).span(4, 2))
                .place(new TestWidgetDefinition("second"), DashboardDsl.at(4, 2).span(2, 2))
                .build());
    }
}
