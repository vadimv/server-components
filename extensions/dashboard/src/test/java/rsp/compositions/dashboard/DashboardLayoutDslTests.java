package rsp.compositions.dashboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DashboardLayoutDslTests {

    @Test
    void builds_layout_with_grid_settings_and_widget_placements() {
        DashboardLayout layout = DashboardDsl.dashboard()
                .columns(8)
                .rowHeightPx(80)
                .gap("12px")
                .place(new TestDashboardWidget("a"), DashboardDsl.at(2, 3).span(4, 2))
                .build();

        assertEquals(8, layout.columns());
        assertEquals(80, layout.rowHeightPx());
        assertEquals("12px", layout.gap());
        assertEquals(1, layout.placements().size());
        assertEquals("a", layout.placements().getFirst().widget().id());
        assertEquals(new GridArea(2, 3, 4, 2), layout.placements().getFirst().area());
    }

    @Test
    void rejects_duplicate_widget_ids() {
        assertThrows(IllegalArgumentException.class, () -> DashboardDsl.dashboard()
                .place(new TestDashboardWidget("same"), DashboardDsl.at(1, 1).span(2, 1))
                .place(new TestDashboardWidget("same"), DashboardDsl.at(3, 1).span(2, 1))
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
        assertThrows(IllegalArgumentException.class, () -> DashboardDsl.dashboard()
                .columns(4)
                .place(new TestDashboardWidget("wide"), DashboardDsl.at(3, 1).span(3, 1))
                .build());
    }

    @Test
    void rejects_invalid_layout_settings() {
        assertThrows(IllegalArgumentException.class, () -> DashboardDsl.dashboard().columns(0).build());
        assertThrows(IllegalArgumentException.class, () -> DashboardDsl.dashboard().rowHeightPx(0).build());
        assertThrows(IllegalArgumentException.class, () -> DashboardDsl.dashboard().gap(" ").build());
    }

    @Test
    void rejects_null_widgets() {
        assertThrows(NullPointerException.class, () -> DashboardDsl.dashboard()
                .place(null, DashboardDsl.at(1, 1).span(1, 1)));
    }
}
