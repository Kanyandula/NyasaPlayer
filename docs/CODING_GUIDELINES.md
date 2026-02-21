# Coding Guidelines

## Code Style

This project follows the official Kotlin and Android conventions:

- [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide)
- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [Compose API Guidelines](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-api-guidelines.md)

Key points:
- **Max line length**: 120 characters
- **Trailing commas**: Required on both call sites and declaration sites
- **Imports**: No wildcard imports; ordered alphabetically
- **Composable functions**: Use PascalCase (enforced by Detekt config)
- **Modifiers**: Every public composable that emits UI must accept a `Modifier` parameter with a default of `Modifier`

## Static Analysis

### Detekt

[Detekt](https://detekt.dev/) is configured at the project root and runs against all source files.

- **Config file**: `config/detekt/detekt.yml`
- **Compose rules**: Provided by [compose-rules](https://github.com/mrmans0n/compose-rules) (`io.nlopez.compose.rules:detekt`)
- **Run manually**: `./gradlew detekt`
- **Report location**: `build/reports/detekt/detekt.html`

The configuration enforces `maxIssues: 0`, meaning any issue will fail the build.

### Android Lint

Android Lint is configured per-module in `app/build.gradle.kts`.

- **Config file**: `app/lint.xml`
- **Run manually**: `./gradlew :app:lintDebug`
- **Report location**: `app/build/reports/lint-results-debug.html`

Custom severity overrides in `lint.xml`:
| Issue | Severity | Reason |
|-------|----------|--------|
| `ContentDescription` | Error | Accessibility is non-negotiable |
| `HardcodedText` | Warning | Strings should be in resources |
| `MissingTranslation` | Warning | Catch missing translations early |
| `UnusedResources` | Warning | Keep the project clean |
| `InvalidPackage` | Ignore | False positive from transitive gRPC/Firebase deps |

## Testing

Write tests for:

1. **Regressions** — Every bug fix should include a test that reproduces the original failure
2. **Complex Logic** — Business logic, state machines, data transformations
3. **Edge Cases** — Null inputs, empty collections, boundary values, error states
4. **Integration Points** — Repository-to-network boundaries, database queries, navigation

Test naming convention:
```kotlin
@Test
fun `should return empty list when no songs match the query`() { ... }
```

## Commit Workflow

A pre-commit hook runs Detekt and Lint before every commit.

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
2. Runs `./gradlew :app:lintDebug` — fails the commit if any lint errors are found

### Bypassing the hook

In rare cases (e.g., work-in-progress commits), you can skip the hook:

```bash
git commit --no-verify -m "WIP: work in progress"
```

Use this sparingly. All checks must pass before merging.
