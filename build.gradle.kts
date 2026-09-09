plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Required because Kotlin 2.0+ decoupled the Jetpack Compose compiler from the
    // Kotlin release cycle. Without this plugin applied (see app/build.gradle.kts),
    // any module with `buildFeatures.compose = true` fails to build.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
