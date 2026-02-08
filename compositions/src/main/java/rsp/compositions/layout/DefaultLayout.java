package rsp.compositions.layout;

import rsp.component.Lookup;
import rsp.component.definitions.Component;
import rsp.compositions.composition.UiRegistry;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.UiComponentResolver;
import rsp.compositions.contract.ViewContract;
import rsp.dsl.Definition;

import java.util.ArrayList;
import java.util.List;

import static java.lang.System.Logger.Level.TRACE;
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
 *   <li>{@code layout-sidebar} - optional left sidebar</li>
 *   <li>{@code layout-primary} - main content area</li>
 *   <li>{@code layout-right-sidebar} - optional right sidebar</li>
 *   <li>{@code modal-overlay / modal-backdrop / modal-content} - overlay modal</li>
 * </ul>
 */
public final class DefaultLayout implements Layout {
    private final System.Logger logger = System.getLogger(getClass().getName());

    @Override
    public Definition resolve(Scene scene, Lookup lookup) {
        logger.log(TRACE, () -> "Resolving a standard content three column layout with optional modal overlay");

        UiRegistry uiRegistry = scene.uiRegistry();

        // Resolve primary contract to UI component
        Component<?> primary = UiComponentResolver.resolve(uiRegistry, scene.primaryContract().getClass());

        // Resolve LEFT_SIDEBAR contract to UI component (if present)
        Component<?> leftSidebar = null;
        ViewContract leftSidebarContract = scene.leftSidebarContract();
        if (leftSidebarContract != null) {
            leftSidebar = UiComponentResolver.resolve(uiRegistry, leftSidebarContract.getClass());
        }

        // Resolve RIGHT_SIDEBAR contract to UI component (if present)
        Component<?> rightSidebar = null;
        ViewContract rightSidebarContract = scene.rightSidebarContract();
        if (rightSidebarContract != null) {
            rightSidebar = UiComponentResolver.resolve(uiRegistry, rightSidebarContract.getClass());
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

        // Build layout children: [left-sidebar?] [primary] [right-sidebar?] [overlay?]
        List<Definition> children = new ArrayList<>();
        children.add(attr("class", "layout-container"));
        if (leftSidebar != null) {
            children.add(div(attr("class", "layout-sidebar"), leftSidebar));
        }
        children.add(div(attr("class", "layout-primary"), primary));
        if (rightSidebar != null) {
            children.add(div(attr("class", "layout-right-sidebar"), rightSidebar));
        }
        if (activeOverlay != null) {
            final Class<? extends ViewContract> overlayToClose = activeOverlayClass;
            children.add(renderOverlay(activeOverlay, () -> lookup.publish(HIDE, overlayToClose)));
        }

        return div(children.toArray(Definition[]::new));
    }

    private static Definition renderOverlay(Component<?> content, Runnable onClose) {
        return div(attr("class", "modal-overlay"),
                div(attr("class", "modal-backdrop"),
                        on("click", _ -> onClose.run())),
                div(attr("class", "modal-content"),
                        content));
    }
}
