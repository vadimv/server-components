package rsp.compositions.composition;

import rsp.component.definitions.Component;
import rsp.compositions.contract.BoundContractComponent;
import rsp.compositions.contract.Contract;

import java.util.*;
import java.util.function.Supplier;

/**
 * Group - Binding of contract component types to their factories and navigation structure.
 * <p>
 * Each {@link #bind} call declares the contract component and its constructor.
 * Groups can be nested via {@link #add} to create a tree structure
 * for navigation and other metadata consumers.
 * <p>
 * Lookup methods ({@link #resolveComponent}, {@link #contractClasses})
 * aggregate across the entire tree (own bindings + all descendants).
 */
public class Group {

    private final String label;
    private String description;
    private final List<Group> children;
    private final Map<Class<? extends Contract>, Supplier<BoundContractComponent>> contractComponents;

    /**
     * Create an unlabeled group (e.g., for system/infrastructure contracts).
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
        this.contractComponents = new LinkedHashMap<>();
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
     * Bind an intent-driven contract component directly.
     *
     * <p>The supplied component is the contract: it owns state, lifecycle,
     * subscriptions, and effects. Its view is therefore rendering-only.</p>
     *
     * @param contractClass concrete contract component class
     * @param componentFactory factory producing a fresh contract component
     * @return this for chaining
     */
    @SuppressWarnings("rawtypes")
    public <C extends Component & Contract> Group bind(Class<C> contractClass,
                                                       Supplier<? extends C> componentFactory) {
        Objects.requireNonNull(contractClass, "contractClass");
        Objects.requireNonNull(componentFactory, "componentFactory");
        contractComponents.put(contractClass, () -> BoundContractComponent.of(componentFactory.get()));
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
     * Resolve the directly bound contract component for the given class.
     *
     * @param contractClass the contract component class
     * @return a fresh contract component instance
     * @throws IllegalStateException if the contract has no direct component binding
     */
    public Component<?, ?> resolveComponent(Class<? extends Contract> contractClass) {
        return resolveBoundComponent(contractClass).component();
    }

    /**
     * Resolve the directly bound contract component and contract runtime for the given class.
     *
     * @param contractClass the contract component class
     * @return a fresh component/contract binding
     * @throws IllegalStateException if the contract has no direct component binding
     */
    public BoundContractComponent resolveBoundComponent(Class<? extends Contract> contractClass) {
        Supplier<BoundContractComponent> factory = findContractComponent(contractClass);
        if (factory == null) {
            throw new IllegalStateException(
                    "No contract component bound for contract: " + contractClass.getName());
        }
        return factory.get();
    }

    /** Returns whether this group tree contains a binding for the contract. */
    public boolean hasBinding(Class<? extends Contract> contractClass) {
        return findContractComponent(contractClass) != null;
    }

    /**
     * Returns all bound contract classes in insertion order, aggregated from own bindings
     * and all descendants.
     *
     * @return unmodifiable set of contract classes
     */
    public Set<Class<? extends Contract>> contractClasses() {
        Set<Class<? extends Contract>> result = new LinkedHashSet<>(contractComponents.keySet());
        for (Group child : children) {
            result.addAll(child.contractClasses());
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns the labeled group path that owns the given contract class.
     * <p>
     * The path is built from this group to the group where the contract was
     * directly bound. Unlabeled groups are skipped, which keeps merged/root
     * infrastructure groups out of placement decisions.
     *
     * @param contractClass The contract class to locate
     * @return the owning labeled path, or empty if the contract is not bound
     */
    public Optional<List<String>> groupPathFor(Class<? extends Contract> contractClass) {
        Objects.requireNonNull(contractClass, "contractClass");
        return groupPathFor(contractClass, List.of());
    }

    /**
     * Returns the labeled group that owns the given contract for placement
     * policy decisions.
     * <p>
     * Unlike {@link #groupPathFor(Class)}, this returns group identity rather
     * than display labels. That keeps sibling groups with the same label
     * distinct for placement. Contracts bound directly to unlabeled groups are
     * treated as having no placement group, so system/infrastructure contracts
     * stay modal unless the layout declares an explicit placement rule.
     *
     * @param contractClass The contract class to locate
     * @return the labeled owning group, or empty if the contract is unknown or
     *         owned by an unlabeled group
     */
    public Optional<Group> placementGroupFor(Class<? extends Contract> contractClass) {
        Objects.requireNonNull(contractClass, "contractClass");
        return placementGroupForInternal(contractClass);
    }

    /**
     * Extract a lightweight metadata tree from this group.
     * Contains only labels and contract classes — no factories or views.
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
                contractClassesInThisGroup());
    }

    private Optional<List<String>> groupPathFor(Class<? extends Contract> contractClass,
                                                List<String> parentPath) {
        List<String> currentPath = parentPath;
        if (label != null) {
            currentPath = new ArrayList<>(parentPath);
            currentPath.add(label);
        }
        if (containsContract(contractClass)) {
            return Optional.of(List.copyOf(currentPath));
        }
        for (Group child : children) {
            Optional<List<String>> childPath = child.groupPathFor(contractClass, currentPath);
            if (childPath.isPresent()) {
                return childPath;
            }
        }
        return Optional.empty();
    }

    private Optional<Group> placementGroupForInternal(Class<? extends Contract> contractClass) {
        if (containsContract(contractClass)) {
            return label != null ? Optional.of(this) : Optional.empty();
        }
        for (Group child : children) {
            Optional<Group> childGroup = child.placementGroupForInternal(contractClass);
            if (childGroup.isPresent()) {
                return childGroup;
            }
        }
        return Optional.empty();
    }

    private Supplier<BoundContractComponent> findContractComponent(Class<? extends Contract> contractClass) {
        Supplier<BoundContractComponent> factory = contractComponents.get(contractClass);
        if (factory != null) {
            return factory;
        }
        for (Group child : children) {
            factory = child.findContractComponent(contractClass);
            if (factory != null) {
                return factory;
            }
        }
        return null;
    }

    private boolean containsContract(Class<? extends Contract> contractClass) {
        return contractComponents.containsKey(contractClass);
    }

    private List<Class<? extends Contract>> contractClassesInThisGroup() {
        return List.copyOf(contractComponents.keySet());
    }
}
