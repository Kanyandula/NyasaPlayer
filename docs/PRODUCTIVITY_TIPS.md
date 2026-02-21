# Productivity Tips

## Running Detekt Locally

```bash
# Full project scan
./gradlew detekt

# View the HTML report
open build/reports/detekt/detekt.html
```

## Running Lint Locally

```bash
# Debug variant lint check
./gradlew :app:lintDebug

# View the HTML report
open app/build/reports/lint-results-debug.html
```

## IDE Plugin Setup

### Detekt in Android Studio

1. Install the **Detekt** plugin from the JetBrains Marketplace
2. Go to **Settings > Tools > Detekt**
3. Enable Detekt
4. Set the configuration file to `config/detekt/detekt.yml`
5. Enable "Build upon the default configuration"

This gives you real-time Detekt feedback as you type.

## Common Issues & Fixes

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

## Suppressing Rules

If a rule produces a false positive, you can suppress it locally:

```kotlin
@Suppress("MagicNumber")
val animationDuration = 300
```

Use this sparingly. If you find yourself suppressing the same rule repeatedly, consider adjusting the rule in `config/detekt/detekt.yml` instead.

## Creating a Baseline

If you're introducing Detekt to a project with many existing issues, you can create a baseline so that only new issues are flagged:

```bash
./gradlew detektBaseline
```

This generates `detekt-baseline.xml` at the project root. Commit this file and Detekt will ignore the baselined issues while catching all new ones.

Similarly for Lint:

```bash
./gradlew :app:lintDebug -Dlint.baselines.continue=true
```

This creates `app/lint-baseline.xml`. Reference it in `build.gradle.kts`:

```kotlin
lint {
    baseline = file("lint-baseline.xml")
}
```
