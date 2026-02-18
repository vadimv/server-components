package rsp.compositions.layout;

import rsp.component.Lookup;
import rsp.component.definitions.Component;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.ViewContract;
import rsp.dsl.Definition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.System.Logger.Level.TRACE;
import static rsp.dsl.Html.*;

/**
 * Default base layout with CSS class-based positioning.
 * <p>
 * Configurable via builder methods that declare which contract classes
 * should appear in which position. These contracts are eagerly instantiated
 * as companions during scene building (via {@link #requiredContracts()}).
 * <p>
 * Structure:
 * <ul>
 *   <li>{@code layout-wrapper} - outer wrapper div</li>
 *   <li>{@code layout-container} - content container</li>
 *   <li>{@code layout-sidebar} - optional left sidebar</li>
 *   <li>{@code layout-primary} - main content area (routed contract)</li>
 *   <li>{@code layout-right-sidebar} - optional right sidebar</li>
 * </ul>
 */
public final class DefaultLayout implements Layout {
    private final System.Logger logger = System.getLogger(getClass().getName());

    private final Class<? extends ViewContract> leftSidebarClass;
    private final Class<? extends ViewContract> rightSidebarClass;
    private final Class<? extends ViewContract> headerClass;

    public DefaultLayout() {
        this(null, null, null);
    }

    private DefaultLayout(Class<? extends ViewContract> leftSidebarClass,
                          Class<? extends ViewContract> rightSidebarClass,
                          Class<? extends ViewContract> headerClass) {
        this.leftSidebarClass = leftSidebarClass;
        this.rightSidebarClass = rightSidebarClass;
        this.headerClass = headerClass;
    }

    public DefaultLayout leftSidebar(Class<? extends ViewContract> contractClass) {
        return new DefaultLayout(contractClass, rightSidebarClass, headerClass);
    }

    public DefaultLayout rightSidebar(Class<? extends ViewContract> contractClass) {
        return new DefaultLayout(leftSidebarClass, contractClass, headerClass);
    }

    public DefaultLayout header(Class<? extends ViewContract> contractClass) {
        return new DefaultLayout(leftSidebarClass, rightSidebarClass, contractClass);
    }

    @Override
    public Set<Class<? extends ViewContract>> requiredContracts() {
        Set<Class<? extends ViewContract>> required = new HashSet<>();
        if (leftSidebarClass != null) required.add(leftSidebarClass);
        if (rightSidebarClass != null) required.add(rightSidebarClass);
        if (headerClass != null) required.add(headerClass);
        return Set.copyOf(required);
    }

    @Override
    public Definition resolve(Scene scene, Lookup lookup) {
        logger.log(TRACE, () -> "Resolving default layout");

        // Resolve routed contract to UI component
        Component<?> primary = null;
        if (scene.routedContract() != null) {
            primary = scene.uiRegistry().resolveView(scene.routedContract().getClass());
        }

        // Resolve companion contracts to UI components
        Component<?> leftSidebar = resolveCompanion(scene, leftSidebarClass);
        Component<?> rightSidebar = resolveCompanion(scene, rightSidebarClass);
        Component<?> header = resolveCompanion(scene, headerClass);

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
        if (primary != null) {
            containerChildren.add(div(attr("class", "layout-primary"), primary));
        }
        if (rightSidebar != null) {
            containerChildren.add(div(attr("class", "layout-right-sidebar"), rightSidebar));
        }

        wrapper.add(div(containerChildren.toArray(Definition[]::new)));

        return div(wrapper.toArray(Definition[]::new));
    }

    private Component<?> resolveCompanion(Scene scene, Class<? extends ViewContract> contractClass) {
        if (contractClass == null) return null;
        ViewContract companion = scene.companionContract(contractClass);
        if (companion == null) return null;
        return scene.uiRegistry().resolveView(companion.getClass());
    }
}
