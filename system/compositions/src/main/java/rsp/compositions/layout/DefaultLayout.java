package rsp.compositions.layout;

import rsp.component.Lookup;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ContractDescriptor;
import rsp.compositions.contract.DirectContractHost;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.Contract;
import rsp.dsl.Definition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.lang.System.Logger.Level.TRACE;
import static rsp.dsl.Html.*;

/**
 * Default base layout with CSS class-based positioning.
 * <p>
 * Configurable via builder methods that declare which contract classes
 * should appear in which position. These contracts become companion descriptors
 * during scene building (via {@link #requiredContracts()}).
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

    private final Class<? extends Contract> leftSidebarClass;
    private final Class<? extends Contract> rightSidebarClass;
    private final Class<? extends Contract> headerClass;
    private final Map<Class<? extends Contract>, Placement> placements;
    private final GroupPlacementPolicy groupPlacementPolicy;

    public DefaultLayout() {
        this(null, null, null, Map.of(), GroupPlacementPolicy.ALL_MODAL);
    }

    private DefaultLayout(Class<? extends Contract> leftSidebarClass,
                          Class<? extends Contract> rightSidebarClass,
                          Class<? extends Contract> headerClass,
                          Map<Class<? extends Contract>, Placement> placements,
                          GroupPlacementPolicy groupPlacementPolicy) {
        this.leftSidebarClass = leftSidebarClass;
        this.rightSidebarClass = rightSidebarClass;
        this.headerClass = headerClass;
        this.placements = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(placements));
        this.groupPlacementPolicy = Objects.requireNonNull(groupPlacementPolicy, "groupPlacementPolicy");
    }

    public DefaultLayout leftSidebar(Class<? extends Contract> contractClass) {
        return new DefaultLayout(contractClass, rightSidebarClass, headerClass,
                placements, groupPlacementPolicy);
    }

    public DefaultLayout rightSidebar(Class<? extends Contract> contractClass) {
        return new DefaultLayout(leftSidebarClass, contractClass, headerClass,
                placements, groupPlacementPolicy);
    }

    public DefaultLayout header(Class<? extends Contract> contractClass) {
        return new DefaultLayout(leftSidebarClass, rightSidebarClass, contractClass,
                placements, groupPlacementPolicy);
    }

    /**
     * Declares the preferred placement for contracts assignable to
     * {@code contractType}.
     * <p>
     * This is a layout hint: future user preferences or fixed framework rules
     * may override it. More specific contract types win over broader base types.
     */
    public DefaultLayout placement(Class<? extends Contract> contractType,
                                   Placement placement) {
        Objects.requireNonNull(contractType, "contractType");
        Objects.requireNonNull(placement, "placement");
        Map<Class<? extends Contract>, Placement> updated = new LinkedHashMap<>(placements);
        updated.put(contractType, placement);
        return new DefaultLayout(leftSidebarClass, rightSidebarClass, headerClass,
                updated, groupPlacementPolicy);
    }

    public DefaultLayout groupPlacementPolicy(GroupPlacementPolicy policy) {
        return new DefaultLayout(leftSidebarClass, rightSidebarClass, headerClass,
                placements, Objects.requireNonNull(policy, "policy"));
    }

    @Override
    public PlacementDecision resolvePlacement(Class<? extends Contract> contractClass,
                                              Scene scene) {
        return PlacementResolver.resolve(contractClass, scene, placements, groupPlacementPolicy,
                scene != null ? scene.contracts() : null);
    }

    @Override
    public Set<Class<? extends Contract>> requiredContracts() {
        Set<Class<? extends Contract>> required = new HashSet<>();
        if (leftSidebarClass != null) required.add(leftSidebarClass);
        if (rightSidebarClass != null) required.add(rightSidebarClass);
        if (headerClass != null) required.add(headerClass);
        return Set.copyOf(required);
    }

    @Override
    public Definition resolve(Scene scene, Lookup lookup) {
        logger.log(TRACE, () -> "Resolving default layout");

        // Resolve routed contract to UI component
        Component<?, ?> primary = null;
        if (scene.routedDescriptor() != null) {
            primary = resolveDescriptor(scene, scene.routedDescriptor());
        }

        // Resolve companion contracts to UI components
        Component<?, ?> leftSidebar = resolveCompanion(scene, leftSidebarClass);
        Component<?, ?> rightSidebar = resolveCompanion(scene, rightSidebarClass);
        Component<?, ?> header = resolveCompanion(scene, headerClass);

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

    private Component<?, ?> resolveCompanion(Scene scene, Class<? extends Contract> contractClass) {
        if (contractClass == null) return null;
        ContractDescriptor descriptor = scene.companionDescriptor(contractClass);
        if (descriptor == null) return null;
        return resolveDescriptor(scene, descriptor);
    }

    private Component<?, ?> resolveDescriptor(Scene scene, ContractDescriptor descriptor) {
        return new DirectContractHost(descriptor, scene.contracts().resolveBoundComponent(descriptor.contractClass()));
    }
}
