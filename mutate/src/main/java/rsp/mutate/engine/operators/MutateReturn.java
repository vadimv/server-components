package rsp.mutate.engine.operators;

import rsp.mutate.engine.Operator;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.Set;

/**
 * Replaces a returned value with a fixed one: {@code boolean}&rarr;{@code true},
 * integral&rarr;{@code 0}, reference&rarr;{@code null}. Probes whether a method's result is actually
 * asserted — e.g. forcing {@code isRetained} to {@code true} makes every keyed child "retained" and
 * breaks the keyed diff.
 *
 * <p>{@code long}/{@code float}/{@code double} returns are not handled in this operator set.
 */
public final class MutateReturn implements Operator {

    private static final Set<ClassDesc> INTEGRAL = Set.of(
            ConstantDescs.CD_int, ConstantDescs.CD_short, ConstantDescs.CD_byte, ConstantDescs.CD_char);

    @Override
    public String id() {
        return "MutateReturn";
    }

    @Override
    public boolean matches(final CodeElement element, final MethodModel method) {
        if (!(element instanceof ReturnInstruction returnInstruction)) {
            return false;
        }
        final ClassDesc returnType = method.methodTypeSymbol().returnType();
        return switch (returnInstruction.typeKind()) {
            case INT -> returnType.equals(ConstantDescs.CD_boolean) || INTEGRAL.contains(returnType);
            case REFERENCE -> !returnType.isPrimitive();
            default -> false;
        };
    }

    @Override
    public void rewrite(final CodeBuilder builder, final CodeElement element, final MethodModel method) {
        final ClassDesc returnType = method.methodTypeSymbol().returnType();
        if (returnType.equals(ConstantDescs.CD_boolean)) {
            builder.pop().loadConstant(1).ireturn();      // force true
        } else if (INTEGRAL.contains(returnType)) {
            builder.pop().loadConstant(0).ireturn();      // force 0
        } else {
            builder.pop().aconst_null().areturn();        // force null
        }
    }
}
