package rsp.mutate.engine.operators;

import rsp.mutate.engine.Operator;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.BranchInstruction;
import java.util.Map;

/**
 * Inverts a conditional branch (e.g. {@code IFEQ}↔{@code IFNE}, {@code IF_ICMPLT}↔{@code IF_ICMPGE}),
 * keeping the same target. Probes inverted guards / wrong-branch logic.
 */
public final class NegateCondition implements Operator {

    private static final Map<Opcode, Opcode> NEGATION = Map.ofEntries(
            Map.entry(Opcode.IFEQ, Opcode.IFNE),
            Map.entry(Opcode.IFNE, Opcode.IFEQ),
            Map.entry(Opcode.IFLT, Opcode.IFGE),
            Map.entry(Opcode.IFGE, Opcode.IFLT),
            Map.entry(Opcode.IFGT, Opcode.IFLE),
            Map.entry(Opcode.IFLE, Opcode.IFGT),
            Map.entry(Opcode.IF_ICMPEQ, Opcode.IF_ICMPNE),
            Map.entry(Opcode.IF_ICMPNE, Opcode.IF_ICMPEQ),
            Map.entry(Opcode.IF_ICMPLT, Opcode.IF_ICMPGE),
            Map.entry(Opcode.IF_ICMPGE, Opcode.IF_ICMPLT),
            Map.entry(Opcode.IF_ICMPGT, Opcode.IF_ICMPLE),
            Map.entry(Opcode.IF_ICMPLE, Opcode.IF_ICMPGT),
            Map.entry(Opcode.IF_ACMPEQ, Opcode.IF_ACMPNE),
            Map.entry(Opcode.IF_ACMPNE, Opcode.IF_ACMPEQ),
            Map.entry(Opcode.IFNULL, Opcode.IFNONNULL),
            Map.entry(Opcode.IFNONNULL, Opcode.IFNULL));

    @Override
    public String id() {
        return "NegateCondition";
    }

    @Override
    public boolean matches(final CodeElement element, final MethodModel method) {
        return element instanceof BranchInstruction branch && NEGATION.containsKey(branch.opcode());
    }

    @Override
    public void rewrite(final CodeBuilder builder, final CodeElement element, final MethodModel method) {
        final BranchInstruction branch = (BranchInstruction) element;
        builder.branch(NEGATION.get(branch.opcode()), branch.target());
    }
}
