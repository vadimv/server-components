package rsp.mutate.engine;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;

/**
 * A mutation operator: it recognises a {@link CodeElement} and rewrites it into a behaviour-changing
 * (but, where possible, verifiable) form. Operators are pure and stateless; the {@link MethodModel}
 * is supplied so an operator can consult method-level context (e.g. the return type).
 */
public interface Operator {

    /** Stable identifier, used in {@link MutationPoint#operatorId()}. */
    String id();

    /** Whether {@code element} (inside {@code method}) is a mutation site for this operator. */
    boolean matches(CodeElement element, MethodModel method);

    /**
     * Emit the mutated replacement for a matched {@code element} into {@code builder}. The result
     * must leave the operand stack balanced; if a rewrite is nonetheless invalid, the mutant fails
     * verification when loaded and is recorded as an error rather than a survivor.
     */
    void rewrite(CodeBuilder builder, CodeElement element, MethodModel method);
}
