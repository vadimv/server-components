package rsp.mutate.run;

import rsp.mutate.engine.MutationPoint;

import java.util.List;

/**
 * The outcome of a mutation run: every mutant and its {@link Verdict}, plus a human-readable
 * rendering whose actionable part is the SURVIVORS list. The mutation score is reported but is
 * informational, not a gate.
 */
public record Report(String target, List<Result> results) {

    /** One mutant's verdict. */
    public record Result(MutationPoint point, Verdict verdict) {
    }

    public long count(final Verdict verdict) {
        return results.stream().filter(r -> r.verdict() == verdict).count();
    }

    public List<Result> survivors() {
        return results.stream().filter(r -> r.verdict() == Verdict.SURVIVED).toList();
    }

    /** The verdict of the first mutant produced by {@code operatorId} in {@code methodName} (for assertions). */
    public Verdict verdictFor(final String operatorId, final String methodName) {
        return results.stream()
                .filter(r -> r.point().operatorId().equals(operatorId) && r.point().methodName().equals(methodName))
                .map(Result::verdict)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no mutant for " + operatorId + " in " + methodName));
    }

    public String render() {
        final long killed = count(Verdict.KILLED);
        final long survived = count(Verdict.SURVIVED);
        final long error = count(Verdict.ERROR);
        final long timeout = count(Verdict.TIMEOUT);
        final long scored = killed + timeout + survived; // non-error mutants
        final StringBuilder sb = new StringBuilder();
        sb.append("Mutation report for ").append(target).append(System.lineSeparator());
        for (final Result r : results) {
            sb.append("  ").append(pad(r.verdict().name())).append("  ").append(r.point()).append(System.lineSeparator());
        }
        sb.append(results.size()).append(" mutants: ")
                .append(killed).append(" killed, ")
                .append(survived).append(" survived, ")
                .append(error).append(" error, ")
                .append(timeout).append(" timeout");
        if (scored > 0) {
            sb.append("  (score ").append(Math.round(100.0 * (killed + timeout) / scored)).append("% killed, informational)");
        }
        sb.append(System.lineSeparator());
        final List<Result> survivors = survivors();
        sb.append("SURVIVORS (").append(survivors.size()).append("):").append(System.lineSeparator());
        for (final Result r : survivors) {
            sb.append("  ").append(r.point()).append(System.lineSeparator());
        }
        return sb.toString();
    }

    private static String pad(final String s) {
        return (s + "        ").substring(0, 8);
    }
}
