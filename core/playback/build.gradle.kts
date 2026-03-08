plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.nyasaplayer.core.playback"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Firebase (needed for AuthRepository's FirebaseUser type)
    // TODO: Remove after Phase 3 replaces FirebaseUser with domain types (see AuthRepository.kt)
    implementation(libs.firebase.auth.ktx)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
