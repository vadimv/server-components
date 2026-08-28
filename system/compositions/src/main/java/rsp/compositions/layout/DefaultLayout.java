package rsp.compositions.layout;

import rsp.compositions.block.Block;

import rsp.component.Lookup;
import rsp.component.definitions.Component;
import rsp.compositions.block.BlockDescriptor;
import rsp.compositions.block.DirectBlockHost;
import rsp.compositions.block.Scene;
import rsp.compositions.block.BlockRuntime;
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
 * Configurable via builder methods that declare which block classes
 * should appear in which position. These blocks become companion descriptors
 * during scene building (via {@link #requiredBlocks()}).
 * <p>
 * Structure:
 * <ul>
 *   <li>{@code layout-wrapper} - outer wrapper div</li>
 *   <li>{@code layout-container} - content container</li>
 *   <li>{@code layout-sidebar} - optional left sidebar</li>
 *   <li>{@code layout-primary} - main content area (routed block)</li>
 *   <li>{@code layout-right-sidebar} - optional right sidebar</li>
 * </ul>
 */
public final class DefaultLayout implements Layout {
    private final System.Logger logger = System.getLogger(getClass().getName());

    private final Class<? extends Block<?, ?>> leftSidebarClass;
    private final Class<? extends Block<?, ?>> rightSidebarClass;
    private final Class<? extends Block<?, ?>> headerClass;
    private final Map<Class<? extends BlockRuntime>, Placement> placements;
    private final GroupPlacementPolicy groupPlacementPolicy;

    public DefaultLayout() {
        this(null, null, null, Map.of(), GroupPlacementPolicy.ALL_MODAL);
    }

    private DefaultLayout(Class<? extends Block<?, ?>> leftSidebarClass,
                          Class<? extends Block<?, ?>> rightSidebarClass,
                          Class<? extends Block<?, ?>> headerClass,
                          Map<Class<? extends BlockRuntime>, Placement> placements,
                          GroupPlacementPolicy groupPlacementPolicy) {
        this.leftSidebarClass = leftSidebarClass;
        this.rightSidebarClass = rightSidebarClass;
        this.headerClass = headerClass;
        this.placements = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(placements));
        this.groupPlacementPolicy = Objects.requireNonNull(groupPlacementPolicy, "groupPlacementPolicy");
    }

    public DefaultLayout leftSidebar(Class<? extends Block<?, ?>> blockClass) {
        return new DefaultLayout(blockClass, rightSidebarClass, headerClass,
                placements, groupPlacementPolicy);
    }

    public DefaultLayout rightSidebar(Class<? extends Block<?, ?>> blockClass) {
        return new DefaultLayout(leftSidebarClass, blockClass, headerClass,
                placements, groupPlacementPolicy);
    }

    public DefaultLayout header(Class<? extends Block<?, ?>> blockClass) {
        return new DefaultLayout(leftSidebarClass, rightSidebarClass, blockClass,
                placements, groupPlacementPolicy);
    }

    /**
     * Declares the preferred placement for blocks assignable to
     * {@code blockType}.
     * <p>
     * This is a layout hint: future user preferences or fixed framework rules
     * may override it. More specific block types win over broader base types.
     */
    public DefaultLayout placement(Class<? extends BlockRuntime> blockType,
                                   Placement placement) {
        Objects.requireNonNull(blockType, "blockType");
        Objects.requireNonNull(placement, "placement");
        Map<Class<? extends BlockRuntime>, Placement> updated = new LinkedHashMap<>(placements);
        updated.put(blockType, placement);
        return new DefaultLayout(leftSidebarClass, rightSidebarClass, headerClass,
                updated, groupPlacementPolicy);
    }

    public DefaultLayout groupPlacementPolicy(GroupPlacementPolicy policy) {
        return new DefaultLayout(leftSidebarClass, rightSidebarClass, headerClass,
                placements, Objects.requireNonNull(policy, "policy"));
    }

    @Override
    public PlacementDecision resolvePlacement(Class<? extends Block<?, ?>> blockClass,
                                              Scene scene) {
        return PlacementResolver.resolve(blockClass, scene, placements, groupPlacementPolicy,
                scene != null ? scene.blocks() : null);
    }

    @Override
    public Set<Class<? extends Block<?, ?>>> requiredBlocks() {
        Set<Class<? extends Block<?, ?>>> required = new HashSet<>();
        if (leftSidebarClass != null) required.add(leftSidebarClass);
        if (rightSidebarClass != null) required.add(rightSidebarClass);
        if (headerClass != null) required.add(headerClass);
        return Set.copyOf(required);
    }

    @Override
    public Definition resolve(Scene scene, Lookup lookup) {
        logger.log(TRACE, () -> "Resolving default layout");

        // Resolve routed block to UI component
        Component<?, ?> primary = null;
        if (scene.routedDescriptor() != null) {
            primary = resolveDescriptor(scene, scene.routedDescriptor());
        }

        // Resolve companion blocks to UI components
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

    private Component<?, ?> resolveCompanion(Scene scene, Class<? extends Block<?, ?>> blockClass) {
        if (blockClass == null) return null;
        BlockDescriptor descriptor = scene.companionDescriptor(blockClass);
        if (descriptor == null) return null;
        return resolveDescriptor(scene, descriptor);
    }

    private Component<?, ?> resolveDescriptor(Scene scene, BlockDescriptor descriptor) {
        return new DirectBlockHost(descriptor, scene.blocks().resolveBlock(descriptor.blockClass()));
    }
}
