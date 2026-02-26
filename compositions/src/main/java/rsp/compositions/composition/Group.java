package rsp.compositions.composition;

import rsp.component.Lookup;
import rsp.component.definitions.Component;
import rsp.compositions.contract.ViewContract;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Group - Unified binding of contract types to their factories, views, and navigation structure.
 * <p>
 * Replaces both {@code Contracts} (factory/view binding) and {@code Category} (navigation grouping).
 * Each {@link #bind} call declares a 1-1-1 relationship: contract type, its constructor,
 * and its view. Groups can be nested via {@link #add} to create a tree structure
 * for navigation and other metadata consumers.
 * <p>
 * Lookup methods ({@link #resolveView}, {@link #contractFactory}, {@link #contractClasses})
 * aggregate across the entire tree (own bindings + all descendants).
 */
public class Group {

    private final String label;
    private final List<Group> children;
    private final Map<Class<? extends ViewContract>, Function<Lookup, ViewContract>> contractFactories;
    private final Map<Class<? extends ViewContract>, Supplier<? extends Component<?>>> views;

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
        this.contractFactories = new LinkedHashMap<>();
        this.views = new LinkedHashMap<>();
    }

    /**
     * Bind a contract type to its factory and view factory.
     *
     * @param contractClass   The concrete contract class
     * @param contractFactory Factory that takes a Lookup and produces a ViewContract
     * @param viewFactory     Factory that creates the UI component for this contract
     * @return this for chaining
     */
    public Group bind(Class<? extends ViewContract> contractClass,
                      Function<Lookup, ViewContract> contractFactory,
                      Supplier<? extends Component<?>> viewFactory) {
        contractFactories.put(contractClass, contractFactory);
        views.put(contractClass, viewFactory);
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
     * Resolve the UI component for the given contract class.
     * Searches own bindings first, then children depth-first.
     *
     * @param contractClass The contract class to resolve
     * @return The UI component instance
     * @throws IllegalStateException if no view factory is bound for this class
     */
    public Component<?> resolveView(Class<? extends ViewContract> contractClass) {
        Supplier<? extends Component<?>> factory = findView(contractClass);
        if (factory == null) {
            throw new IllegalStateException(
                    "No UI component bound for contract: " + contractClass.getName());
        }
        return factory.get();
    }

    /**
     * Returns the contract factory for the given class, or null if not bound.
     * Searches own bindings first, then children depth-first.
     *
     * @param contractClass The contract class
     * @return The factory, or null
     */
    public Function<Lookup, ViewContract> contractFactory(Class<? extends ViewContract> contractClass) {
        Function<Lookup, ViewContract> factory = contractFactories.get(contractClass);
        if (factory != null) {
            return factory;
        }
        for (Group child : children) {
            factory = child.contractFactory(contractClass);
            if (factory != null) {
                return factory;
            }
        }
        return null;
    }

    /**
     * Returns all bound contract classes in insertion order, aggregated from own bindings
     * and all descendants.
     *
     * @return unmodifiable set of contract classes
     */
    public Set<Class<? extends ViewContract>> contractClasses() {
        Set<Class<? extends ViewContract>> result = new LinkedHashSet<>(contractFactories.keySet());
        for (Group child : children) {
            result.addAll(child.contractClasses());
        }
        return Collections.unmodifiableSet(result);
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
                List.copyOf(childNodes),
                List.copyOf(contractFactories.keySet()));
    }

    private Supplier<? extends Component<?>> findView(Class<? extends ViewContract> contractClass) {
        Supplier<? extends Component<?>> factory = views.get(contractClass);
        if (factory != null) {
            return factory;
        }
        for (Group child : children) {
            factory = child.findView(contractClass);
            if (factory != null) {
                return factory;
            }
        }
        return null;
    }
}
