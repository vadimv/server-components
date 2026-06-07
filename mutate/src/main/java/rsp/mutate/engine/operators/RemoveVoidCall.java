package rsp.mutate.engine.operators;

import rsp.mutate.engine.Operator;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

/**
 * Drops a {@code void}-returning method call, replacing it with stack-balancing pops. Probes whether
 * a side effect is actually asserted — the bug class behind the survived {@code htmlBuilder.reset()}.
 *
 * <p>Constructor-chaining calls ({@code <init>}) are never targeted: dropping {@code super()} always
 * yields unverifiable bytecode and is never a meaningful test of behaviour.
 */
public final class RemoveVoidCall implements Operator {

    @Override
    public String id() {
        return "RemoveVoidCall";
    }

    @Override
    public boolean matches(final CodeElement element, final MethodModel method) {
        return element instanceof InvokeInstruction invoke
                && invoke.typeSymbol().returnType().equals(ConstantDescs.CD_void)
                && !invoke.name().stringValue().equals("<init>");
    }

    @Override
    public void rewrite(final CodeBuilder builder, final CodeElement element, final MethodModel method) {
        final InvokeInstruction invoke = (InvokeInstruction) element;
        final MethodTypeDesc descriptor = invoke.typeSymbol();
        final List<ClassDesc> parameters = descriptor.parameterList();
        // Pop arguments in reverse, then the receiver (unless the call is static).
        for (int i = parameters.size() - 1; i >= 0; i--) {
            pop(builder, parameters.get(i));
        }
        if (invoke.opcode() != Opcode.INVOKESTATIC) {
            builder.pop(); // a receiver always occupies one slot
        }
    }

    private static void pop(final CodeBuilder builder, final ClassDesc type) {
        if (type.equals(ConstantDescs.CD_long) || type.equals(ConstantDescs.CD_double)) {
            builder.pop2();
        } else {
            builder.pop();
        }
    }
}
