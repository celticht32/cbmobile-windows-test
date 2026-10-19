// Root build file: plugin versions only (applied in :app).
// Toolchain band (see README): AGP 8.x + Gradle 8.9 (wrapper) + Kotlin 2.0.x, run on Gradle JDK 17-22.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
