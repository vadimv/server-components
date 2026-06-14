# `mutate` — deterministic mutation-testing harness

A small, zero-dependency tool that measures **test adequacy**: do the tests actually catch a
deliberate change to the production code? Line coverage proves a line *ran*; mutation testing proves
a line is *asserted*. A dropped `htmlBuilder.reset()` once passed with full line coverage of its
method — mutation testing is what catches that.

This is **test tooling, not part of the published `rsp` library.** The engine uses only the JDK
Class-File API (`java.lang.classfile`, GA in JDK 24; this project runs 25); the runner adds
`junit-platform-launcher`. Nothing here ships in the library artifacts.

> **Status — M1 (deterministic core, run manually).** No LLM. It enumerates mutants, runs the
> covering tests against each in a fresh JVM, and prints a survivor report you read by hand. The
> design, phasing, and where this fits the QA strategy are in
> [`docs/mutation-harness-design.md`](../docs/mutation-harness-design.md) and
> [`compositions/TEST_STRATEGY.md`](../compositions/TEST_STRATEGY.md) ("Test Adequacy").

## How it works

For a target class and the test classes that cover it, the harness first **runs the tests against the
unmutated class as a baseline** — if they are not all green (a covering test already fails, or cannot
be discovered/run), it aborts with `BaselineFailedException`, because otherwise every mutant would be
trivially "killed" and the report falsely perfect. Then it:

1. **enumerates** mutants of the target's bytecode (one per applicable operator site);
2. **applies** each mutant (Class-File API), writing the mutated `.class` to a temp dir;
3. **forks a JVM** with that temp dir first on the classpath, so the mutant **shadows** the original
   (no java agent, no in-process classloader tricks). The inherited classpath is the one the harness
   itself runs on, so a mutant sees the real test classpath;
4. runs the named tests via the JUnit Platform `Launcher` and records a **verdict** from the exit
   code.

| Verdict    | Meaning                                                                                  |
|------------|------------------------------------------------------------------------------------------|
| `KILLED`   | a test failed — the change was detected (good)                                           |
| `SURVIVED` | all tests passed — a **gap**: the actionable result                                      |
| `ERROR`    | the mutant failed to load/verify, or test discovery failed (never counted as a survivor) |
| `TIMEOUT`  | the run exceeded its budget, e.g. a mutated loop guard (counted as killed)               |

An invalid mutant fails JVM verification → `ERROR`: the fork **force-loads (links and verifies) the
target** before running the tests, so a broken rewrite is caught even when no selected test
references it — it can never masquerade as a survivor.

## Operators (M1)

| Operator          | Change                                                                    | Bug class it probes                |
|-------------------|---------------------------------------------------------------------------|------------------------------------|
| `RemoveVoidCall`  | drops a `void` method call (stack-balanced pops); never touches `<init>`  | a side effect that nothing asserts |
| `NegateCondition` | inverts a conditional branch (`IFEQ`↔`IFNE`, `IF_ICMPLT`↔`IF_ICMPGE`, …)  | inverted guard / wrong branch      |
| `MutateReturn`    | forces the return value: `boolean`→`true`, integral→`0`, reference→`null` | a result that nothing asserts      |

## Quick start

Use it from a module that depends on `mutate` at **test scope** (see `core/pom.xml` for the
dependency). Drive it from a **gated test** so it runs only on demand and never in normal CI:

```java
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "mutate.run", matches = "true")
class MutationDriverTest {
    @org.junit.jupiter.api.Test
    void mutate_my_class() {
        Report report = Mutate.run(
                "rsp.dom.NodesTreeDiff",                       // target (binary class name)
                List.of("rsp.dom.NodesTreeDiffPropertyTests"));// covering test classes
        System.out.println(report.render());

        // Optional: pin known fixtures so a regression in the *tests* fails the run.
        // assertEquals(Verdict.KILLED, report.verdictFor("MutateReturn", "isRetained"));
    }
}
```

Run it (the `-Dmutate.run=true` switch enables the gated test; run from the owning module so the
forked mutants inherit that module's full test classpath):

```bash
mvn -q -pl core -am test -Dtest=MutationHarnessManualTest -Dmutate.run=true -DfailIfNoTests=false -Dpbt.tries=2000
```

A real example lives at
[`core/.../MutationHarnessManualTest.java`](../core/src/test/java/rsp/dom/MutationHarnessManualTest.java).


## API

- `Mutate.run(String targetClass, List<String> testClasses)` — default 120s per-mutant timeout.
- `Mutate.run(String targetClass, List<String> testClasses, Duration perMutantTimeout)`.
- `Report` — `render()`, `survivors()`, `count(Verdict)`, `results()`, and
  `verdictFor(operatorId, methodName)` for assertions.
- `MutationEngine` — `enumerate(byte[])` / `apply(byte[], MutationPoint)` if you want the engine
  without the runner.

## Constraints

- **Deterministic, fast tests only.** Flaky/e2e tests produce *false kills*; mutation testing pairs
  with unit + fast PBT + deterministic session tests.
- **Run from the owning module's test JVM** so the fork inherits the right classpath. The target
  class is read from the harness's own classpath.
- **Debug info** (`-g`, the Maven default) is needed for source line numbers in the report.

