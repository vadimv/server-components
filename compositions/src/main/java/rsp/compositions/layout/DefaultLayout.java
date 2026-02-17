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

        // Resolve HEADER contract to UI component (if present)
        Component<?> header = null;
        ViewContract headerContract = scene.headerContract();
        if (headerContract != null) {
            header = UiComponentResolver.resolve(scene.uiRegistry(), headerContract.getClass());
        }

        // Build layout: [header?] then container with [left-sidebar?] [primary] [right-sidebar?]
        List<Definition> wrapper = new ArrayList<>();
        wrapper.add(attr("class", "layout-wrapper"));

        if (header != null) {
            wrapper.add(header);
        }

        List<Definition> containerChildren = new ArrayList<>();
        containerChildren.add(attr("class", "layout-container"));
        if (leftSidebar != null) {
            containerChildren.add(div(attr("class", "layout-sidebar"), leftSidebar));
        }
        containerChildren.add(div(attr("class", "layout-primary"), primary));
        if (rightSidebar != null) {
            containerChildren.add(div(attr("class", "layout-right-sidebar"), rightSidebar));
        }

        wrapper.add(div(containerChildren.toArray(Definition[]::new)));

        return div(wrapper.toArray(Definition[]::new));
    }
}
