// buildSrc/build.gradle.kts
// Shared build logic (Kotlin DSL helpers + version preprocessor) used by the
// per-loader build scripts (build-fabric.gradle.kts / build-neoforge.gradle.kts).

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}
