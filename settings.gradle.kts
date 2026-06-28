// settings.gradle.kts
// Stonecutter multi-version / multi-loader project setup for chat-upgrade.
// Each registered Minecraft-version x loader pair becomes a subproject under versions/.

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9"
}

stonecutter {
    kotlinController = true
    shared {
        /**
         * Registers one Minecraft-version x loader pair. The project directory is
         * seeded from gradle/targets/<version>.properties, so release targets and
         * compatibility-only patch targets can share the same build scripts while
         * keeping dependency coordinates explicit.
         */
        fun target(loader: String, version: String) {
            val targetDir = file("versions/$version-$loader")
            if (!targetDir.exists()) targetDir.mkdirs()

            val sourceProps = file("gradle/targets/$version.properties")
            val targetProps = file("versions/$version-$loader/gradle.properties")
            if (sourceProps.exists()) sourceProps.copyTo(targetProps, overwrite = true)

            val buildscript = when (loader) {
                "fabric" -> "build-fabric.gradle.kts"
                "neoforge" -> "build-neoforge.gradle.kts"
                else -> error("Unsupported loader: $loader")
            }

            version("$version-$loader", version).buildscript(buildscript)
        }

        fun mc(loader: String, vararg versions: String) {
            for (version in versions) target(loader, version)
        }

        fun mcCompat(loader: String, vararg versions: String) {
            for (version in versions) target(loader, version)
        }

        // Publishable release-line targets. CI/release discovery intentionally reads only these `mc(...)` calls.
        mc("fabric", "26.1", "26.2")
        mc("neoforge", "26.1", "26.2")

        // Compatibility-only patch targets for direct runClient/runServer checks.
        // They are registered as Gradle projects, but their properties disable standalone jar output.
        mcCompat("fabric", "26.1.1", "26.1.2")
        mcCompat("neoforge", "26.1.1", "26.1.2")

        // 26.2 currently has no released 26.2.x patch target in the upstream version manifest.
        // Add future 26.2.x releases here with matching gradle/targets/<version>.properties.
    }
    create(rootProject)
}

rootProject.name = "chat-upgrade"
