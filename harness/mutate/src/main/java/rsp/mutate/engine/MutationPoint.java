package rsp.mutate.engine;

/**
 * A single applicable mutation, identified deterministically so the catalog is reproducible across
 * runs and machines.
 *
 * <p>Identity is {@code (class, method, descriptor, operator, ordinal)} rather than a raw bytecode
 * offset: {@code ordinal} is the index of this match <em>among the same operator's matches within
 * the same method</em>. {@link MutationEngine#enumerate} and {@link MutationEngine#apply} share one
 * matcher, so they always agree on which element an ordinal refers to.
 *
 * @param binaryClassName e.g. {@code rsp.dom.NodesTreeDiff}
 * @param methodName      method name ({@code <init>} for constructors)
 * @param methodDescriptor JVM descriptor, disambiguating overloads
 * @param operatorId      the operator that produced this mutation
 * @param ordinal         0-based index among that operator's matches in this method
 * @param sourceLine      source line from the {@code LineNumberTable}, or {@code -1} if absent
 */
public record MutationPoint(String binaryClassName,
                            String methodName,
                            String methodDescriptor,
                            String operatorId,
                            int ordinal,
                            int sourceLine) {

    /** A stable, human-referenceable id. */
    public String id() {
        return binaryClassName + "#" + methodName + methodDescriptor + ":" + operatorId + "#" + ordinal;
    }

    /** The source file name inferred from the binary class name, e.g. {@code NodesTreeDiff.java}. */
    public String sourceFile() {
        final String simple = binaryClassName.substring(binaryClassName.lastIndexOf('.') + 1);
        final int nested = simple.indexOf('$');
        return (nested >= 0 ? simple.substring(0, nested) : simple) + ".java";
    }

    @Override
    public String toString() {
        return sourceFile() + ":" + sourceLine + "  " + operatorId + "  " + methodName + methodDescriptor;
    }
}
