package rsp.compositions.layout;

import rsp.component.definitions.Component;
import rsp.dsl.Definition;

import static rsp.dsl.Html.*;

/**
 * Default layout with CSS class-based slot rendering.
 * <p>
 * Structure:
 * <ul>
 *   <li>{@code layout-container} - wrapper div</li>
 *   <li>{@code layout-sidebar} - optional sidebar (left of primary)</li>
 *   <li>{@code layout-primary} - main content area</li>
 *   <li>{@code modal-overlay / modal-backdrop / modal-content} - overlay modal</li>
 * </ul>
 */
public final class DefaultLayout implements Layout {

    @Override
    public Definition render(Component<?> primary,
                             Component<?> sidebar,
                             Component<?> activeOverlay,
                             Runnable onOverlayClose) {
        if (activeOverlay == null) {
            if (sidebar == null) {
                return div(attr("class", "layout-container"),
                        div(attr("class", "layout-primary"), primary));
            }
            return div(attr("class", "layout-container"),
                    div(attr("class", "layout-sidebar"), sidebar),
                    div(attr("class", "layout-primary"), primary));
        }

        Definition overlay = renderOverlay(activeOverlay, onOverlayClose);
        if (sidebar == null) {
            return div(attr("class", "layout-container"),
                    div(attr("class", "layout-primary"), primary),
                    overlay);
        }
        return div(attr("class", "layout-container"),
                div(attr("class", "layout-sidebar"), sidebar),
                div(attr("class", "layout-primary"), primary),
                overlay);
    }

    private static Definition renderOverlay(Component<?> content, Runnable onClose) {
        return div(attr("class", "modal-overlay"),
                div(attr("class", "modal-backdrop"),
                        on("click", ctx -> onClose.run())),
                div(attr("class", "modal-content"),
                        content));
    }
}
