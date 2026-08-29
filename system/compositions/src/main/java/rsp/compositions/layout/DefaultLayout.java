package rsp.compositions.layout;

import rsp.compositions.block.Block;

import rsp.component.Lookup;
import rsp.component.definitions.Component;
import rsp.compositions.block.BlockDescriptor;
import rsp.compositions.block.DirectBlockHost;
import rsp.compositions.block.Scene;
import rsp.compositions.block.BlockRuntime;
import rsp.compositions.block.BlockTarget;
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

    private final Object leftSidebarKey;
    private final Object rightSidebarKey;
    private final Object headerKey;
    private final Map<Class<? extends BlockRuntime>, Placement> placements;
    private final GroupPlacementPolicy groupPlacementPolicy;

    public DefaultLayout() {
        this(null, null, null, Map.of(), GroupPlacementPolicy.ALL_MODAL);
    }

    private DefaultLayout(Object leftSidebarKey,
                          Object rightSidebarKey,
                          Object headerKey,
                          Map<Class<? extends BlockRuntime>, Placement> placements,
                          GroupPlacementPolicy groupPlacementPolicy) {
        this.leftSidebarKey = leftSidebarKey;
        this.rightSidebarKey = rightSidebarKey;
        this.headerKey = headerKey;
        this.placements = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(placements));
        this.groupPlacementPolicy = Objects.requireNonNull(groupPlacementPolicy, "groupPlacementPolicy");
    }

    public DefaultLayout leftSidebar(Class<? extends Block<?, ?>> blockClass) {
        return leftSidebar((Object) blockClass);
    }

    public DefaultLayout leftSidebar(Object blockKey) {
        return new DefaultLayout(Objects.requireNonNull(blockKey, "blockKey"), rightSidebarKey, headerKey,
                placements, groupPlacementPolicy);
    }

    public DefaultLayout rightSidebar(Class<? extends Block<?, ?>> blockClass) {
        return rightSidebar((Object) blockClass);
    }

    public DefaultLayout rightSidebar(Object blockKey) {
        return new DefaultLayout(leftSidebarKey, Objects.requireNonNull(blockKey, "blockKey"), headerKey,
                placements, groupPlacementPolicy);
    }

    public DefaultLayout header(Class<? extends Block<?, ?>> blockClass) {
        return header((Object) blockClass);
    }

    public DefaultLayout header(Object blockKey) {
        return new DefaultLayout(leftSidebarKey, rightSidebarKey, Objects.requireNonNull(blockKey, "blockKey"),
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
        return new DefaultLayout(leftSidebarKey, rightSidebarKey, headerKey,
                updated, groupPlacementPolicy);
    }

    public DefaultLayout groupPlacementPolicy(GroupPlacementPolicy policy) {
        return new DefaultLayout(leftSidebarKey, rightSidebarKey, headerKey,
                placements, Objects.requireNonNull(policy, "policy"));
    }

    @Override
    public PlacementDecision resolvePlacement(Class<? extends Block<?, ?>> blockClass,
                                              Scene scene) {
        return resolvePlacement(new BlockTarget(blockClass, blockClass), scene);
    }

    @Override
    public PlacementDecision resolvePlacement(BlockTarget target, Scene scene) {
        return PlacementResolver.resolve(target, scene, placements, groupPlacementPolicy,
                scene != null ? scene.blocks() : null);
    }

    @Override
    public Set<Class<? extends Block<?, ?>>> requiredBlocks() {
        Set<Class<? extends Block<?, ?>>> required = new HashSet<>();
        if (leftSidebarKey instanceof Class<?> cls) required.add(asBlockClass(cls));
        if (rightSidebarKey instanceof Class<?> cls) required.add(asBlockClass(cls));
        if (headerKey instanceof Class<?> cls) required.add(asBlockClass(cls));
        return Set.copyOf(required);
    }

    @Override
    public Set<Object> requiredBlockKeys() {
        Set<Object> required = new HashSet<>();
        if (leftSidebarKey != null) required.add(leftSidebarKey);
        if (rightSidebarKey != null) required.add(rightSidebarKey);
        if (headerKey != null) required.add(headerKey);
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
        Component<?, ?> leftSidebar = resolveCompanion(scene, leftSidebarKey);
        Component<?, ?> rightSidebar = resolveCompanion(scene, rightSidebarKey);
        Component<?, ?> header = resolveCompanion(scene, headerKey);

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

    private Component<?, ?> resolveCompanion(Scene scene, Object blockKey) {
        if (blockKey == null) return null;
        BlockDescriptor descriptor = scene.companionDescriptor(blockKey);
        if (descriptor == null) return null;
        return resolveDescriptor(scene, descriptor);
    }

    private Component<?, ?> resolveDescriptor(Scene scene, BlockDescriptor descriptor) {
        return new DirectBlockHost(descriptor, scene.blocks().resolveBlock(descriptor.blockKey()));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Block<?, ?>> asBlockClass(Class<?> type) {
        return (Class<? extends Block<?, ?>>) type;
    }
}
