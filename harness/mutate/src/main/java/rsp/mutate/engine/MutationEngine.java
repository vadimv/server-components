package rsp.mutate.engine;

import rsp.mutate.engine.operators.MutateReturn;
import rsp.mutate.engine.operators.NegateCondition;
import rsp.mutate.engine.operators.RemoveVoidCall;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.LineNumber;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enumerates and applies mutations on compiled class bytes using the JDK Class-File API. Pure and
 * deterministic: {@link #enumerate} is a function of the bytecode and the operator set, and
 * {@link #apply} uses the <em>same</em> operator matcher, so a {@link MutationPoint}'s ordinal always
 * refers to the same instruction.
 *
 * <p>Zero dependencies beyond {@code java.lang.classfile}.
 */
public final class MutationEngine {

    private static final ClassFile CLASS_FILE = ClassFile.of();

    /** Operators in a fixed order; enumeration order depends on it, so it is part of the catalog identity. */
    private final List<Operator> operators = List.of(
            new RemoveVoidCall(),
            new NegateCondition(),
            new MutateReturn());

    /**
     * All applicable mutations for a class, in deterministic order (method order → element order →
     * operator order).
     */
    public List<MutationPoint> enumerate(final byte[] classBytes) {
        final ClassModel classModel = CLASS_FILE.parse(classBytes);
        final String className = classModel.thisClass().asInternalName().replace('/', '.');
        final List<MutationPoint> points = new ArrayList<>();

        for (final MethodModel method : classModel.methods()) {
            if (method.code().isEmpty()) {
                continue;
            }
            final String methodName = method.methodName().stringValue();
            final String methodDescriptor = method.methodTypeSymbol().descriptorString();
            final Map<String, Integer> nextOrdinal = new HashMap<>();
            int line = -1;

            for (final CodeElement element : method.code().get()) {
                if (element instanceof LineNumber lineNumber) {
                    line = lineNumber.line();
                    continue;
                }
                for (final Operator operator : operators) {
                    if (operator.matches(element, method)) {
                        final int ordinal = nextOrdinal.merge(operator.id(), 0, (existing, ignored) -> existing + 1);
                        points.add(new MutationPoint(className, methodName, methodDescriptor,
                                operator.id(), ordinal, line));
                    }
                }
            }
        }
        return points;
    }

    /**
     * Returns the class bytes with the single mutation at {@code point} applied. Validity is not
     * checked here — an invalid mutant fails verification when loaded and is recorded as an error by
     * the runner, never as a survivor.
     */
    public byte[] apply(final byte[] classBytes, final MutationPoint point) {
        final Operator operator = operatorById(point.operatorId());
        final ClassModel classModel = CLASS_FILE.parse(classBytes);
        final MethodModel target = classModel.methods().stream()
                .filter(method -> method.methodName().stringValue().equals(point.methodName())
                        && method.methodTypeSymbol().descriptorString().equals(point.methodDescriptor()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no such method for " + point.id()));

        final int[] seen = {0};
        final CodeTransform codeTransform = (builder, element) -> {
            if (operator.matches(element, target)) {
                if (seen[0]++ == point.ordinal()) {
                    operator.rewrite(builder, element, target);
                    return;
                }
            }
            builder.with(element);
        };
        final ClassTransform classTransform = ClassTransform.transformingMethodBodies(
                method -> method.methodName().stringValue().equals(point.methodName())
                        && method.methodTypeSymbol().descriptorString().equals(point.methodDescriptor()),
                codeTransform);
        return CLASS_FILE.transformClass(classModel, classTransform);
    }

    private Operator operatorById(final String id) {
        for (final Operator operator : operators) {
            if (operator.id().equals(id)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("unknown operator: " + id);
    }
}
