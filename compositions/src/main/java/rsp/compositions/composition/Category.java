package rsp.compositions.composition;

import rsp.compositions.contract.ViewContract;

import java.util.*;

/**
 * Category - Explicit grouping for contracts (e.g., Posts, Comments).
 * <p>
 * Used for Explorer/navigation grouping and for deriving display titles.
 * </p>
 */
public final class Category {

    private final String label;
    private final Map<Class<? extends ViewContract>, Category> contractIndex;
    private final List<Category> groups;

    /**
     * Root category container.
     */
    public Category() {
        this(null, new LinkedHashMap<>(), new ArrayList<>());
    }

    /**
     * Group category with label.
     */
    public Category(String label) {
        this(label, new LinkedHashMap<>(), new ArrayList<>());
    }

    private Category(String label,
                     Map<Class<? extends ViewContract>, Category> contractIndex,
                     List<Category> groups) {
        this.label = label;
        this.contractIndex = contractIndex;
        this.groups = groups;
    }

    /**
     * Register a group and map its contracts to the group.
     */
    @SafeVarargs
    public final Category group(Category category, Class<? extends ViewContract>... contracts) {
        Objects.requireNonNull(category, "category");
        if (category.label == null || category.label.isBlank()) {
            throw new IllegalArgumentException("Category label cannot be null/blank");
        }
        groups.add(category);
        if (contracts != null) {
            for (Class<? extends ViewContract> contractClass : contracts) {
                Objects.requireNonNull(contractClass, "contractClass");
                contractIndex.put(contractClass, category);
            }
        }
        return this;
    }

    /**
     * Resolve metadata for a contract class.
     */
    public ContractMetadata metadataFor(Class<? extends ViewContract> contractClass) {
        Objects.requireNonNull(contractClass, "contractClass");
        Category category = contractIndex.get(contractClass);
        if (category != null) {
            String categoryLabel = category.label;
            return new ContractMetadata(categoryLabel, categoryLabel);
        }
        return defaultMetadata(contractClass);
    }

    private static ContractMetadata defaultMetadata(Class<? extends ViewContract> contractClass) {
        String label = defaultLabel(contractClass);
        return new ContractMetadata(label, label);
    }

    private static String defaultLabel(Class<? extends ViewContract> contractClass) {
        String name = contractClass.getSimpleName();
        name = name.replaceAll("Contract\\d*$", "");

        boolean isCreate = name.contains("Create");
        boolean isEdit = name.contains("Edit");
        boolean isList = name.endsWith("List");

        String base = name;
        if (isCreate) {
            base = base.replace("Create", "");
        } else if (isEdit) {
            base = base.replace("Edit", "");
        } else if (isList) {
            base = base.substring(0, base.length() - "List".length());
        }

        String human = humanize(base);
        if (isCreate) {
            return "Create " + singularize(human);
        }
        if (isEdit) {
            return "Edit " + singularize(human);
        }
        return human;
    }

    private static String singularize(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.endsWith("s") && trimmed.length() > 1) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String humanize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String withSpaces = input.replaceAll("([a-z])([A-Z])", "$1 $2");
        return withSpaces.trim();
    }
}
