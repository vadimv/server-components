package rsp.page;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultConnectionLostWidgetTests {

    @Test
    void default_connection_lost_widget_renders_to_div() {
        Assertions.assertTrue(DefaultConnectionLostWidget.HTML.startsWith("<div"));
        Assertions.assertTrue(DefaultConnectionLostWidget.HTML.endsWith("</div>"));
    }
}
