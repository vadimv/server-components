package rsp.compositions.layout;

import rsp.component.Lookup;
import rsp.component.definitions.Component;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.UiComponentResolver;
import rsp.compositions.contract.ViewContract;
import rsp.dsl.Definition;

import java.util.ArrayList;
import java.util.List;

import static java.lang.System.Logger.Level.TRACE;
import static rsp.dsl.Html.*;

/**
 * Default base layout with CSS class-based slot rendering.
 * <p>
 * Resolves primary and sidebar contracts from the Scene and renders them
 * in a standard content layout. Overlay/layer rendering is handled by
 * {@link LayerLayout} implementations via LayerComponent.
 * <p>
 * Structure:
 * <ul>
 *   <li>{@code layout-container} - wrapper div</li>
 *   <li>{@code layout-sidebar} - optional left sidebar</li>
 *   <li>{@code layout-primary} - main content area</li>
 *   <li>{@code layout-right-sidebar} - optional right sidebar</li>
 * </ul>
 */
public final class DefaultLayout implements Layout {
    private final System.Logger logger = System.getLogger(getClass().getName());

    @Override
    public Definition resolve(Scene scene, Lookup lookup) {
        logger.log(TRACE, () -> "Resolving a standard content three column layout");

        // Resolve primary contract to UI component
        Component<?> primary = UiComponentResolver.resolve(scene.uiRegistry(), scene.primaryContract().getClass());

        // Resolve LEFT_SIDEBAR contract to UI component (if present)
        Component<?> leftSidebar = null;
        ViewContract leftSidebarContract = scene.leftSidebarContract();
        if (leftSidebarContract != null) {
            leftSidebar = UiComponentResolver.resolve(scene.uiRegistry(), leftSidebarContract.getClass());
        }

        // Resolve RIGHT_SIDEBAR contract to UI component (if present)
        Component<?> rightSidebar = null;
        ViewContract rightSidebarContract = scene.rightSidebarContract();
        if (rightSidebarContract != null) {
            rightSidebar = UiComponentResolver.resolve(scene.uiRegistry(), rightSidebarContract.getClass());
        }

        // Build layout children: [left-sidebar?] [primary] [right-sidebar?]
        List<Definition> children = new ArrayList<>();
        children.add(attr("class", "layout-container"));
        if (leftSidebar != null) {
            children.add(div(attr("class", "layout-sidebar"), leftSidebar));
        }
        children.add(div(attr("class", "layout-primary"), primary));
        if (rightSidebar != null) {
            children.add(div(attr("class", "layout-right-sidebar"), rightSidebar));
        }

        return div(children.toArray(Definition[]::new));
    }
}
