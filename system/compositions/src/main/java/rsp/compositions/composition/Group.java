package rsp.compositions.composition;

import rsp.compositions.block.Block;
import rsp.compositions.block.BlockTarget;
import rsp.url.routing.RouteTable;
import rsp.url.routing.RouteTemplate;

import java.util.*;
import java.util.function.Supplier;

/**
 * Group - Binding of block component types to their factories and navigation structure.
 * <p>
 * Each {@link #add(String, Class, Supplier)} call declares a routed block and its constructor.
 * {@link #bind} declares supporting or otherwise non-routed blocks.
 * Groups can be nested via {@link #add} to create a tree structure
 * for navigation and other metadata consumers.
 * <p>
 * Lookup methods ({@link #resolveBlock}, {@link #blockClasses})
 * aggregate across the entire tree (own bindings + all descendants).
 */
public class Group {

    private final String label;
    private String description;
    private final List<Group> children;
    private final Map<Object, Binding<?>> blocks;
    private final List<RoutedTarget> routes;
    private boolean sealed;

    /**
     * Create an unlabeled group (e.g., for system/infrastructure blocks).
     */
    public Group() {
        this(null);
    }

    /**
     * Create a labeled group (e.g., "Posts", "Comments", "Admin").
     *
     * @param label The display label for this group
     */
    public Group(String label) {
        this.label = label;
        this.children = new ArrayList<>();
        this.blocks = new LinkedHashMap<>();
        this.routes = new ArrayList<>();
    }

    /**
     * Set a description for this group, providing richer context for AI agents
     * and other metadata consumers beyond the short label.
     *
     * @param description A natural-language description of this group's purpose
     * @return this for chaining
     */
    public Group description(String description) {
        ensureMutable();
        this.description = description;
        return this;
    }

    /**
     * Bind an intent-driven block component directly.
     *
     * <p>The supplied component is the block: it owns state, lifecycle,
     * subscriptions, and effects. Its view is therefore rendering-only.</p>
     *
     * @param blockClass concrete block component class
     * @param blockFactory factory producing a fresh block component
     * @return this for chaining
     */
    public <S, I, B extends Block<S, I>> Group bind(Class<B> blockClass,
                                                    Supplier<? extends B> blockFactory) {
        return bind(blockClass, blockClass, blockFactory);
    }

    /** Bind the exact typed target used by a route table. */
    public <S, I, B extends Block<S, I>> Group bind(BlockTarget target,
                                                    Supplier<? extends B> blockFactory) {
        Objects.requireNonNull(target, "target");
        @SuppressWarnings("unchecked")
        Class<B> blockClass = (Class<B>) target.blockClass();
        return bind(target.key(), blockClass, blockFactory);
    }

    /**
     * Bind a block under an application-defined key.
     *
     * @param blockKey binding identity used by routing and navigation
     * @param blockClass concrete block component class
     * @param blockFactory factory producing a fresh block component
     * @return this for chaining
     */
    public <S, I, B extends Block<S, I>> Group bind(Object blockKey,
                                                    Class<B> blockClass,
                                                    Supplier<? extends B> blockFactory) {
        ensureMutable();
        Objects.requireNonNull(blockKey, "blockKey");
        Objects.requireNonNull(blockClass, "blockClass");
        Objects.requireNonNull(blockFactory, "blockFactory");
        if (blocks.containsKey(blockKey)) {
            throw new IllegalArgumentException("Duplicate block key in group " + displayName()
                    + ": " + blockKey);
        }
        blocks.put(blockKey, new Binding<>(new BlockTarget(blockKey, blockClass), blockClass, blockFactory));
        return this;
    }

    /**
     * Add a routed block using its class as both binding identity and runtime type.
     *
     * @param routeTemplate route template selecting the block
     * @param blockClass concrete block component class
     * @param blockFactory factory producing a fresh block component
     * @return this for chaining
     */
    public <S, I, B extends Block<S, I>> Group add(String routeTemplate,
                                                   Class<B> blockClass,
                                                   Supplier<? extends B> blockFactory) {
        return add(routeTemplate, blockClass, blockClass, blockFactory);
    }

    /**
     * Add a routed block under an explicit binding identity.
     *
     * <p>This is the advanced form for configuring the same block class more
     * than once. Most applications should use the class-keyed overload.</p>
     */
    public <S, I, B extends Block<S, I>> Group add(String routeTemplate,
                                                   Object blockKey,
                                                   Class<B> blockClass,
                                                   Supplier<? extends B> blockFactory) {
        ensureMutable();
        RouteTemplate route = RouteTemplate.parse(Objects.requireNonNull(routeTemplate, "template"));
        bind(blockKey, blockClass, blockFactory);
        routes.add(new RoutedTarget(route, new BlockTarget(blockKey, blockClass)));
        return this;
    }

    /** Add another route for an already bound class-keyed block. */
    public Group route(String routeTemplate, Class<? extends Block<?, ?>> blockClass) {
        return route(routeTemplate, (Object) blockClass);
    }

    /** Add another route for an already bound block identity. */
    public Group route(String routeTemplate, Object blockKey) {
        ensureMutable();
        RouteTemplate route = RouteTemplate.parse(Objects.requireNonNull(routeTemplate, "template"));
        routes.add(new RoutedTarget(route, target(blockKey)));
        return this;
    }

    /**
     * Add a child group.
     *
     * @param child The child group to add
     * @return this for chaining
     */
    public Group add(Group child) {
        ensureMutable();
        Objects.requireNonNull(child, "child");
        children.add(child);
        return this;
    }

    /**
     * Resolve the directly bound block component for the given class.
     *
     * @param blockClass the block component class
     * @return a fresh block component instance
     * @throws IllegalStateException if the block has no direct component binding
     */
    public Block<?, ?> resolveBlock(Class<? extends Block<?, ?>> blockClass) {
        return resolveBlock((Object) blockClass);
    }

    /** Resolve a fresh block instance by its configured key. */
    public Block<?, ?> resolveBlock(Object blockKey) {
        Binding<?> binding = findBinding(Objects.requireNonNull(blockKey, "blockKey"));
        if (binding == null) {
            throw new IllegalStateException(
                    "No block bound for key: " + blockKey);
        }
        return binding.create();
    }

    /** Returns whether this group tree contains a binding for the block. */
    public boolean hasBinding(Class<? extends Block<?, ?>> blockClass) {
        return hasBinding((Object) blockClass);
    }

    /** Returns whether this group tree contains a binding for the key. */
    public boolean hasBinding(Object blockKey) {
        return blockKey != null && findBinding(blockKey) != null;
    }

    /** Return the configured key/class pair, failing when the key is unknown. */
    public BlockTarget target(Object blockKey) {
        Binding<?> binding = findBinding(Objects.requireNonNull(blockKey, "blockKey"));
        if (binding == null) {
            throw new IllegalStateException("No block bound for key: " + blockKey);
        }
        return binding.target();
    }

    /** All configured targets in insertion and group traversal order. */
    public Set<BlockTarget> blockTargets() {
        LinkedHashMap<Object, BlockTarget> result = new LinkedHashMap<>();
        collectTargets(result, new ArrayList<>());
        return Collections.unmodifiableSet(new LinkedHashSet<>(result.values()));
    }

    /**
     * Returns all bound block classes in insertion order, aggregated from own bindings
     * and all descendants.
     *
     * @return unmodifiable set of block classes
     */
    public Set<Class<? extends Block<?, ?>>> blockClasses() {
        Set<Class<? extends Block<?, ?>>> result = new LinkedHashSet<>();
        for (BlockTarget target : blockTargets()) {
            result.add(target.blockClass());
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns the labeled group path that owns the given block class.
     * <p>
     * The path is built from this group to the group where the block was
     * directly bound. Unlabeled groups are skipped, which keeps merged/root
     * infrastructure groups out of placement decisions.
     *
     * @param blockClass The block class to locate
     * @return the owning labeled path, or empty if the block is not bound
     */
    public Optional<List<String>> groupPathFor(Class<? extends Block<?, ?>> blockClass) {
        return groupPathFor((Object) blockClass);
    }

    /** Returns the labeled owning path for a configured block key. */
    public Optional<List<String>> groupPathFor(Object blockKey) {
        Objects.requireNonNull(blockKey, "blockKey");
        return groupPathFor(blockKey, List.of());
    }

    /**
     * Returns the labeled group that owns the given block for placement
     * policy decisions.
     * <p>
     * Unlike {@link #groupPathFor(Class)}, this returns group identity rather
     * than display labels. That keeps sibling groups with the same label
     * distinct for placement. Blocks bound directly to unlabeled groups are
     * treated as having no placement group, so system/infrastructure blocks
     * stay modal unless the layout declares an explicit placement rule.
     *
     * @param blockClass The block class to locate
     * @return the labeled owning group, or empty if the block is unknown or
     *         owned by an unlabeled group
     */
    public Optional<Group> placementGroupFor(Class<? extends Block<?, ?>> blockClass) {
        return placementGroupFor((Object) blockClass);
    }

    /** Returns the labeled owning group for a configured block key. */
    public Optional<Group> placementGroupFor(Object blockKey) {
        Objects.requireNonNull(blockKey, "blockKey");
        return placementGroupForInternal(blockKey);
    }

    /**
     * Extract a lightweight metadata tree from this group.
     * Contains only labels and block classes — no factories or views.
     *
     * @return the structure tree rooted at this group
     */
    public StructureNode structureTree() {
        List<StructureNode> childNodes = new ArrayList<>();
        for (Group child : children) {
            childNodes.add(child.structureTree());
        }
        return new StructureNode(label,
                description,
                List.copyOf(childNodes),
                blockClassesInThisGroup(),
                blockTargetsInThisGroup());
    }

    void validateUniqueKeys() {
        collectTargets(new LinkedHashMap<>(), new ArrayList<>());
    }

    void contributeRoutes(RouteTable.Builder<BlockTarget> target) {
        routes.forEach(route -> target.route(route.template(), route.target()));
        children.forEach(child -> child.contributeRoutes(target));
    }

    void seal() {
        sealRecursively();
    }

    private Optional<List<String>> groupPathFor(Object blockKey,
                                                List<String> parentPath) {
        List<String> currentPath = parentPath;
        if (label != null) {
            currentPath = new ArrayList<>(parentPath);
            currentPath.add(label);
        }
        if (containsBlock(blockKey)) {
            return Optional.of(List.copyOf(currentPath));
        }
        for (Group child : children) {
            Optional<List<String>> childPath = child.groupPathFor(blockKey, currentPath);
            if (childPath.isPresent()) {
                return childPath;
            }
        }
        return Optional.empty();
    }

    private Optional<Group> placementGroupForInternal(Object blockKey) {
        if (containsBlock(blockKey)) {
            return label != null ? Optional.of(this) : Optional.empty();
        }
        for (Group child : children) {
            Optional<Group> childGroup = child.placementGroupForInternal(blockKey);
            if (childGroup.isPresent()) {
                return childGroup;
            }
        }
        return Optional.empty();
    }

    private Binding<?> findBinding(Object blockKey) {
        Binding<?> binding = blocks.get(blockKey);
        if (binding != null) {
            return binding;
        }
        for (Group child : children) {
            binding = child.findBinding(blockKey);
            if (binding != null) {
                return binding;
            }
        }
        return null;
    }

    private boolean containsBlock(Object blockKey) {
        return blocks.containsKey(blockKey);
    }

    private List<Class<? extends Block<?, ?>>> blockClassesInThisGroup() {
        List<Class<? extends Block<?, ?>>> result = new ArrayList<>();
        for (Binding<?> binding : blocks.values()) {
            result.add(binding.target().blockClass());
        }
        return List.copyOf(result);
    }

    private List<BlockTarget> blockTargetsInThisGroup() {
        return blocks.values().stream().map(Binding::target).toList();
    }

    private void collectTargets(Map<Object, BlockTarget> result, List<String> parentPath) {
        List<String> path = parentPath;
        if (label != null) {
            path = new ArrayList<>(parentPath);
            path.add(label);
        }
        for (Binding<?> binding : blocks.values()) {
            BlockTarget previous = result.putIfAbsent(binding.target().key(), binding.target());
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate block key across group tree at "
                        + (path.isEmpty() ? "<root>" : String.join(" / ", path))
                        + ": " + binding.target().key());
            }
        }
        for (Group child : children) {
            child.collectTargets(result, path);
        }
    }

    private void sealRecursively() {
        sealed = true;
        children.forEach(Group::sealRecursively);
    }

    private void ensureMutable() {
        if (sealed) {
            throw new IllegalStateException("Group is sealed by a Composition and cannot be modified");
        }
    }

    private String displayName() {
        return label == null ? "<unlabeled>" : "'" + label + "'";
    }

    private record Binding<B extends Block<?, ?>>(BlockTarget target,
                                                   Class<B> blockClass,
                                                   Supplier<? extends B> factory) {
        private Binding {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(blockClass, "blockClass");
            Objects.requireNonNull(factory, "factory");
        }

        private B create() {
            B block = factory.get();
            if (block == null) {
                throw new IllegalStateException("Block factory returned null for key: " + target.key());
            }
            return blockClass.cast(block);
        }
    }

    private record RoutedTarget(RouteTemplate template, BlockTarget target) {
        private RoutedTarget {
            Objects.requireNonNull(template, "template");
            Objects.requireNonNull(target, "target");
        }
    }
}
