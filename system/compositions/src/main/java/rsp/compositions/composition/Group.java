package rsp.compositions.composition;

import rsp.compositions.block.Block;

import java.util.*;
import java.util.function.Supplier;

/**
 * Group - Binding of block component types to their factories and navigation structure.
 * <p>
 * Each {@link #bind} call declares the block component and its constructor.
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
    private final Map<Class<? extends Block<?, ?>>, Supplier<? extends Block<?, ?>>> blocks;

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
    }

    /**
     * Set a description for this group, providing richer context for AI agents
     * and other metadata consumers beyond the short label.
     *
     * @param description A natural-language description of this group's purpose
     * @return this for chaining
     */
    public Group description(String description) {
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
        Objects.requireNonNull(blockClass, "blockClass");
        Objects.requireNonNull(blockFactory, "blockFactory");
        blocks.put(blockClass, blockFactory);
        return this;
    }

    /**
     * Add a child group.
     *
     * @param child The child group to add
     * @return this for chaining
     */
    public Group add(Group child) {
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
        Supplier<? extends Block<?, ?>> factory = findBlockFactory(blockClass);
        if (factory == null) {
            throw new IllegalStateException(
                    "No block bound for block class: " + blockClass.getName());
        }
        return factory.get();
    }

    /** Returns whether this group tree contains a binding for the block. */
    public boolean hasBinding(Class<? extends Block<?, ?>> blockClass) {
        return findBlockFactory(blockClass) != null;
    }

    /**
     * Returns all bound block classes in insertion order, aggregated from own bindings
     * and all descendants.
     *
     * @return unmodifiable set of block classes
     */
    public Set<Class<? extends Block<?, ?>>> blockClasses() {
        Set<Class<? extends Block<?, ?>>> result = new LinkedHashSet<>(blocks.keySet());
        for (Group child : children) {
            result.addAll(child.blockClasses());
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
        Objects.requireNonNull(blockClass, "blockClass");
        return groupPathFor(blockClass, List.of());
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
        Objects.requireNonNull(blockClass, "blockClass");
        return placementGroupForInternal(blockClass);
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
                blockClassesInThisGroup());
    }

    private Optional<List<String>> groupPathFor(Class<? extends Block<?, ?>> blockClass,
                                                List<String> parentPath) {
        List<String> currentPath = parentPath;
        if (label != null) {
            currentPath = new ArrayList<>(parentPath);
            currentPath.add(label);
        }
        if (containsBlock(blockClass)) {
            return Optional.of(List.copyOf(currentPath));
        }
        for (Group child : children) {
            Optional<List<String>> childPath = child.groupPathFor(blockClass, currentPath);
            if (childPath.isPresent()) {
                return childPath;
            }
        }
        return Optional.empty();
    }

    private Optional<Group> placementGroupForInternal(Class<? extends Block<?, ?>> blockClass) {
        if (containsBlock(blockClass)) {
            return label != null ? Optional.of(this) : Optional.empty();
        }
        for (Group child : children) {
            Optional<Group> childGroup = child.placementGroupForInternal(blockClass);
            if (childGroup.isPresent()) {
                return childGroup;
            }
        }
        return Optional.empty();
    }

    private Supplier<? extends Block<?, ?>> findBlockFactory(Class<? extends Block<?, ?>> blockClass) {
        Supplier<? extends Block<?, ?>> factory = blocks.get(blockClass);
        if (factory != null) {
            return factory;
        }
        for (Group child : children) {
            factory = child.findBlockFactory(blockClass);
            if (factory != null) {
                return factory;
            }
        }
        return null;
    }

    private boolean containsBlock(Class<? extends Block<?, ?>> blockClass) {
        return blocks.containsKey(blockClass);
    }

    private List<Class<? extends Block<?, ?>>> blockClassesInThisGroup() {
        return List.copyOf(blocks.keySet());
    }
}
