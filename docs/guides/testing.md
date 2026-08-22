# Testing

The default Maven build runs JUnit unit tests, component and session tests, and
the repository's fast property tests. Browser integration tests use the `*IT`
suffix and are excluded unless the `test-all` profile is enabled.

## Common Commands

Run the normal repository test suite:

```bash
mvn test
```

Run one module and build required upstream modules:

```bash
mvn -pl system/compositions -am test
```

Create all normal artifacts using the same lifecycle as CI:

```bash
mvn package
```

Run browser and other `*IT` tests in addition to the normal suite:

```bash
mvn -Ptest-all test
```

The integration profile starts real servers and uses Playwright. It is slower
and requires browser binaries supported by the configured Playwright version.

## Suite Boundaries

| Kind | Current convention | Default build |
| --- | --- | --- |
| Unit/component/session | `*Test`, `*Tests` | Included |
| Property-based | `*PropertyTests` using `harness/pbt` | Included |
| Browser/external integration | `*IT` | Only with `-Ptest-all` |
| Mutation adequacy | gated driver tests using `harness/mutate` | Manual |

Keep deterministic tests in the default suite. Use browser tests for behavior
that only a real DOM, browser history, or authentication redirect can verify.
Mutation testing evaluates the strength of deterministic tests; it is not a
replacement for another test layer.

## Property-Based Tests

The in-house `pbt` harness integrates with ordinary JUnit tests and supports
generators, shrinking, classification, and replayable seeds.

Control a run with system properties:

```bash
mvn test -Dpbt.tries=2000
mvn test -Dpbt.seed=123456789
```

A failure prints its seed. Re-run that seed before changing the generator or
property so the failure remains reproducible.

The current harness generates independent values; it does not yet provide a
state-machine or command-sequence API.

## Mutation Testing

Mutation drivers are disabled unless `mutate.run=true`. See the
[mutation-testing guide](mutation-testing.md) for supported operators,
constraints, and the complete command.

## CI Behavior

GitHub Actions currently runs:

```bash
mvn -B package --file pom.xml
```

That includes the normal test suite but not `*IT`. Treat broader browser,
long-running property, and mutation campaigns as explicit jobs rather than
silently adding them to the fast default build.
