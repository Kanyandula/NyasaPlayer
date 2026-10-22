# Coding Guidelines

## Code Style

This project follows the official Kotlin and Android conventions:

- [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide)
- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [Compose API Guidelines](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-api-guidelines.md)

Key points:
- **Max line length**: 120 characters
- **Trailing commas**: Required on both call sites and declaration sites
- **Imports**: No wildcard imports; ordered by Detekt's `ImportOrdering` (`*`, then `java`, `javax`,
  `kotlin`, then aliases)
- **Composable functions**: Use PascalCase (enforced by Detekt config)
- **Modifiers**: Every public composable that emits UI must accept a `Modifier` parameter with a default of `Modifier`

## Static Analysis

### Detekt

[Detekt](https://detekt.dev/) is configured at the project root. It scans each module's
`src/main/java` (plus `app/src/main/kotlin`), not test sources.

- **Config file**: `config/detekt/detekt.yml`
- **Compose rules**: Provided by [compose-rules](https://github.com/mrmans0n/compose-rules) (`io.nlopez.compose.rules:detekt`)
- **Run manually**: `./gradlew detekt`
- **Report location**: `build/reports/detekt/detekt.html`

The configuration enforces `maxIssues: 0`: any finding not already in `detekt-baseline.xml` fails
the build. `./gradlew detektBaseline` absorbs every current finding into that file — never run it
to turn a red build green. Prefer splitting to suppressing a size threshold: the file-level
`TooManyFunctions` suppressions that exist are recorded debt (`aaos-DESIGN.md` D23), not precedent.

### Android Lint

Only `:app` configures Lint (`lintConfig = file("lint.xml")`); `:core:*` and `:automotive` run the
default severities.

- **Run manually** (both `:automotive` flavors — linting only `oem` leaves the Play-facing manifest
  unchecked):

  ```bash
  ./gradlew :app:lintDebug :core:common:lintDebug :core:data:lintDebug \
            :core:playback:lintDebug :automotive:lintOemDebug :automotive:lintPlaystoreDebug
  ```
- **Reports**: `<module>/build/reports/lint-results-<variant>.html`, e.g.
  `automotive/build/reports/lint-results-oemDebug.html`

Severity overrides in `app/lint.xml` (`:app` only):
| Issue | Severity | Reason |
|-------|----------|--------|
| `ContentDescription` | Error | Accessibility is non-negotiable |
| `HardcodedText` | Warning | Strings should be in resources |
| `MissingTranslation` | Warning | Catch missing translations early |
| `UnusedResources` | Warning | Keep the project clean |
| `InvalidPackage` | Ignore | False positive from transitive gRPC/Firebase deps |

### Common issues and fixes

| Issue | Fix |
|-------|-----|
| `MagicNumber` | Extract the number to a `const val` or named parameter |
| `MaxLineLength` | Break the line at a logical point (before `.`, after `,`) |
| `FunctionNaming` for Composables | Ensure the function is annotated with `@Composable` — PascalCase is allowed for composables |
| `ModifierMissing` | Add a `modifier: Modifier = Modifier` parameter to your composable |
| `ModifierWithoutDefault` | Add `= Modifier` as the default value for your modifier parameter |
| `RememberMissing` | Wrap expensive computations inside composables with `remember { }` |
| `ViewModelInjection` | Inject ViewModels via `hiltViewModel()` at the top-level composable, pass state down |
| `ContentDescription` (Lint) | Add `contentDescription` to `Image` and `Icon` composables, or use `null` with a comment explaining why |
| `HardcodedText` (Lint) | Extract the string to `res/values/strings.xml` |
| `UnusedResources` (Lint) | Remove unused drawables, strings, layouts, etc. |

## Testing

Write tests for:

1. **Regressions** — Every bug fix should include a test that reproduces the original failure
2. **Complex Logic** — Business logic, state machines, data transformations
3. **Edge Cases** — Null inputs, empty collections, boundary values, error states
4. **Integration Points** — Repository-to-network boundaries, database queries, navigation

Test naming convention — `methodUnderTest_condition_expectedResult`, or a backtick sentence (the
`:automotive` norm):
```kotlin
@Test
fun getSongsByIds_emptyList_returnsEmpty() = runTest { }
```

A test proves something only if breaking the code fails it. When checking that by mutation: a
predicate needs both constants tried — a test asserting `true` cannot detect "always true" — and
an "asserts nothing happened" test needs the mutation that makes something happen. Treat a run
with no `tests completed` line in Gradle's output as invalid, not as a pass: a compile failure in
`ksp` reports zero failed tests.

## Commit Workflow

A pre-commit hook is *available*, but **do not assume it is running.**

> **It is inactive wherever `core.hooksPath` is set.** On this machine it points at
> `~/.claude/git-hooks`, so git never reads `.git/hooks/` — which is exactly where
> `install-hooks.sh` writes. Check with `git config --get core.hooksPath`; if it prints
> anything, the hook below is not enforcing anything and Detekt/Lint are manual gates.

### Installing the hook

```bash
./scripts/install-hooks.sh
```

Or via Gradle:

```bash
./gradlew installGitHooks
```

### What the hook does

1. Runs `./gradlew detekt` — fails the commit if any issues are found
2. Runs lint — fails the commit if any lint errors are found

The hook's lint invocation predates `:core:playback` and `:automotive`; run those two
yourself regardless of hook state. All checks must pass before merging.
