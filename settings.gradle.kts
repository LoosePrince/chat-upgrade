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
         * Registers a set of Minecraft versions for one loader. Ensures the target
         * directory exists and seeds its gradle.properties from gradle/targets/<version>.properties.
         */
        fun mc(loader: String, vararg versions: String) {
            for (version in versions) {
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
        }

        // --- Supported targets (MVP: 26.1 and 26.2) ---
        mc("fabric", "26.1", "26.2")
        mc("neoforge", "26.1", "26.2")
    }
    create(rootProject)
}

rootProject.name = "chat-upgrade"
