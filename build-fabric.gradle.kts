// build-fabric.gradle.kts
// Build script applied to every Fabric target subproject (e.g. 26.1-fabric, 26.2-fabric).
// Uses the official Fabric Loom; mappings are omitted because Minecraft 26.x ships unobfuscated.

plugins {
    // Loom 1.17 builds both 26.1 and 26.2 (both unobfuscated). The local cache currently only
    // holds 1.15.5; networked builds will fetch 1.17. Offline verification uses a separate
    // harness pinned to the cached 1.15.5 (see tools/verify-fabric-26.1).
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

val minecraftTitle = mod.prop("mc_title")
val loader = stonecutter.current.project.substringAfterLast('-') // "fabric"
val javaVersion = mod.prop("java_version")
val embedFfmpegNatives = (findProperty("embedFfmpegNatives") ?: "false").toString().toBoolean()

version = "${mod.version}+$minecraftTitle"
group = mod.group
base {
    archivesName.set("${mod.name}-$loader")
}

sourceSets {
    named("main") {
        resources.srcDir(rootProject.file("src/common/src/main/resources"))
        resources.srcDir(rootProject.file("src/fabric/src/main/resources"))
    }
}

// Merge common + fabric Java sources, applying version preprocessing.
versionedJavaSources(
    rootProject.file("src/common/src/main/java"),
    rootProject.file("src/fabric/src/main/java")
)

repositories {
    maven("https://maven.fabricmc.net/")
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.terraformersmc.com/")
}

dependencies {
    // Minecraft 26.x ships unobfuscated, so no `mappings` dependency is declared.
    minecraft("com.mojang:minecraft:${mod.dep("minecraft.fabric")}")
    implementation("net.fabricmc:fabric-loader:${mod.dep("fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${mod.dep("fabric_api_version")}")

    // Bundled (Jar-in-Jar) libraries: ImageIO SPI (WebP / animated) + JavaCPP FFmpeg.
    val bundled = mutableListOf(
        "com.twelvemonkeys.common:common-lang:3.12.0",
        "com.twelvemonkeys.common:common-io:3.12.0",
        "com.twelvemonkeys.common:common-image:3.12.0",
        "com.twelvemonkeys.imageio:imageio-core:3.12.0",
        "com.twelvemonkeys.imageio:imageio-metadata:3.12.0",
        "com.twelvemonkeys.imageio:imageio-jpeg:3.12.0",
        "com.twelvemonkeys.imageio:imageio-webp:3.12.0",
        "org.apache.commons:commons-imaging:1.0.0-alpha5",
        "org.bytedeco:javacpp:1.5.11",
        "org.bytedeco:ffmpeg:7.1-1.5.11"
    )
    if (embedFfmpegNatives) {
        for (classifier in listOf("windows-x86_64", "linux-x86_64", "linux-arm64", "macosx-x86_64", "macosx-arm64")) {
            bundled += "org.bytedeco:javacpp:1.5.11:$classifier"
            bundled += "org.bytedeco:ffmpeg:7.1-1.5.11:$classifier"
        }
    }
    for (notation in bundled) {
        add("implementation", notation)
        add("include", notation)
    }
}

val requiredJava = JavaVersion.toVersion(javaVersion)
java {
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion.toInt())
}

loom {
    runs {
        named("client") {
            // Allow AWT dialogs / clipboard (file pickers) to work during dev runs.
            vmArg("-Djava.awt.headless=false")
            runDir = project.projectDir.toPath()
                .relativize(rootProject.file("run/${project.name}/client").toPath())
                .toString()
        }
        named("server") {
            runDir = project.projectDir.toPath()
                .relativize(rootProject.file("run/${project.name}/server").toPath())
                .toString()
        }
    }
}

tasks.processResources {
    properties(
        listOf("fabric.mod.json"),
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "minecraft" to mod.prop("mc_targets"),
        "java" to javaVersion,
        "fabric_loader" to mod.dep("fabric_loader")
    )
    properties(
        listOf("*.mixins.json"),
        "java" to javaVersion
    )
}

// Collect the production jar into build/libs/<modversion>/fabric/ for convenience.
// Unobfuscated Minecraft (26.x) uses non-remapping Loom: output is `jar`, not `remapJar`.
tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.named("jar"))
    into(rootProject.layout.buildDirectory.dir("libs/${mod.version}/$loader"))
    dependsOn("build")
}
