package rsp.compositions.layout;

import rsp.component.Lookup;
import rsp.component.definitions.Component;
import rsp.compositions.composition.UiRegistry;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.UiComponentResolver;
import rsp.compositions.contract.ViewContract;
import rsp.dsl.Definition;

import static rsp.compositions.contract.EventKeys.HIDE;
import static rsp.dsl.Html.*;

/**
 * Default layout with CSS class-based slot rendering.
 * <p>
 * Resolves primary, sidebar, and overlay contracts from the Scene,
 * then renders them in a standard content layout with optional modal overlay.
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
    public Definition resolve(Scene scene, Lookup lookup) {
        UiRegistry uiRegistry = scene.uiRegistry();

        // Resolve primary contract to UI component
        Component<?> primary = UiComponentResolver.resolve(
                uiRegistry, scene.primaryContract().getClass());

        // Resolve LEFT_SIDEBAR contract to UI component (if present)
        Component<?> sidebar = null;
        ViewContract sidebarContract = scene.leftSidebarContract();
        if (sidebarContract != null) {
            sidebar = UiComponentResolver.resolve(uiRegistry, sidebarContract.getClass());
        }

        // Determine active overlay and resolve to UI component
        Component<?> activeOverlay = null;
        Class<? extends ViewContract> activeOverlayClass = scene.autoOpenContract();
        if (activeOverlayClass == null && scene.hasNonPrimaryContracts()) {
            activeOverlayClass = scene.nonPrimaryContracts().keySet().iterator().next();
        }
        if (activeOverlayClass != null) {
            activeOverlay = UiComponentResolver.resolve(uiRegistry, activeOverlayClass);
        }

        // Render
        if (activeOverlay == null) {
            if (sidebar == null) {
                return div(attr("class", "layout-container"),
                        div(attr("class", "layout-primary"), primary));
            }
            return div(attr("class", "layout-container"),
                    div(attr("class", "layout-sidebar"), sidebar),
                    div(attr("class", "layout-primary"), primary));
        }

        final Class<? extends ViewContract> overlayToClose = activeOverlayClass;
        Definition overlay = renderOverlay(activeOverlay,
                () -> lookup.publish(HIDE, overlayToClose));

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
