# Codex review brief — T25 implementation plan (2026-09-14)

**Target:** `docs/superpowers/plans/2026-09-14-t25-crashlytics.md`, implementing
`docs/tickets/T25-crashlytics-in-core-data.md` with the decisions in `docs/tickets/T24-crash-reporting.md`.
Review the plan against the code and build files. Ranked findings with file:line. Do not edit files.

## Decided — do not re-litigate
T24 D1–D8: one Firebase app; SDK in `:core:data`; plugin per app; debug off by a debug-source-set
manifest flag; Crashlytics 19.4.4 + plugin 3.0.8 (verified on Google Maven; 19.4.4 asks
firebase-common 21.0.0); no Kotlin in T25.

## Least confident — look here first
1. Will the catalog aliases resolve as written (`libs.firebase.crashlytics`,
   `libs.plugins.firebase.crashlytics`) given the existing naming in `gradle/libs.versions.toml`?
2. Does a meta-data element in `:core:data`'s **debug** source set manifest actually merge into every
   debug variant of both apps (including `oemDebug` / `playstoreDebug`, whose build type is `debug`),
   and into no release variant? Any flavor/buildType matching rule in the app modules that breaks it?
3. Task 1 Step 6's shell checks — the merged-manifest `find` path, the `aapt2 dump resources` grep for
   `mapping_file_id` — do they check what they claim, or could they pass for the wrong reason?
4. Does applying the Crashlytics plugin to `:automotive`'s `playstore` flavor or the release unit-test
   disablement (T23) interact badly with anything in `automotive/build.gradle.kts`?
