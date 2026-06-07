package rsp.mutate.run;

/**
 * The outcome of running the covering tests against one mutant.
 *
 * <ul>
 *   <li>{@code KILLED} — at least one test failed: the change was detected.</li>
 *   <li>{@code SURVIVED} — all tests passed: a gap, the actionable result.</li>
 *   <li>{@code ERROR} — the mutant failed to load/verify, or test discovery failed.</li>
 *   <li>{@code TIMEOUT} — the run exceeded its budget (counted as killed; a mutated loop guard).</li>
 * </ul>
 */
public enum Verdict {
    KILLED,
    SURVIVED,
    ERROR,
    TIMEOUT;

    /** Whether the change was detected (killed or timed out). */
    public boolean detected() {
        return this == KILLED || this == TIMEOUT;
    }
}
