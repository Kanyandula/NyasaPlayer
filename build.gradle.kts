// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    source.setFrom(
        files(
            "app/src/main/java",
            "app/src/main/kotlin",
            "core/common/src/main/java",
            "core/data/src/main/java",
            "core/playback/src/main/java",
        )
    )
}

dependencies {
    detektPlugins(libs.detekt.compose.rules)
    detektPlugins(libs.detekt.formatting)
}

tasks.register<Copy>("installGitHooks") {
    description = "Installs the pre-commit git hook from scripts/pre-commit"
    group = "git hooks"
    from("scripts/pre-commit")
    into(".git/hooks")
    filePermissions {
        user {
            read = true
            write = true
            execute = true
        }
        group {
            read = true
            execute = true
        }
        other {
            read = true
            execute = true
        }
    }
}
