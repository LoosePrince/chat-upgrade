// build-neoforge.gradle.kts
// Build script applied to every NeoForge target subproject (e.g. 26.1-neoforge, 26.2-neoforge).
// Uses the official NeoForge ModDevGradle plugin.

plugins {
    id("net.neoforged.moddev") version "2.0.141"
}

val minecraftTitle = mod.prop("mc_title")
val loader = stonecutter.current.project.substringAfterLast('-') // "neoforge"
val javaVersion = mod.prop("java_version")
val projectName = project.name
val releaseTarget = mod.prop("release_target").toBoolean()
val embedFfmpegNatives = (findProperty("embedFfmpegNatives") ?: "false").toString().toBoolean()

version = "${mod.version}+$minecraftTitle"
group = mod.group
base {
    archivesName.set("${mod.name}-$loader")
}

sourceSets {
    named("main") {
        resources.srcDir(rootProject.file("src/common/src/main/resources"))
        resources.srcDir(rootProject.file("src/neoforge/src/main/resources"))
    }
    named("test") {
        java.srcDir(rootProject.file("src/common/src/test/java"))
    }
}

// Merge common + neoforge Java sources, applying version preprocessing.
versionedJavaSources(
    rootProject.file("src/common/src/main/java"),
    rootProject.file("src/neoforge/src/main/java")
)

repositories {
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.terraformersmc.com/")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.4")

    // Bundled libraries: ImageIO SPI (WebP / animated) + JavaCPP FFmpeg.
    // `implementation` puts them on the compile/dev classpath; `jarJar` embeds them in the release jar.
    val bundled = mutableListOf(
        "com.twelvemonkeys.common:common-lang:3.12.0",
        "com.twelvemonkeys.common:common-io:3.12.0",
        "com.twelvemonkeys.common:common-image:3.12.0",
        "com.twelvemonkeys.imageio:imageio-core:3.12.0",
        "com.twelvemonkeys.imageio:imageio-metadata:3.12.0",
        "com.twelvemonkeys.imageio:imageio-jpeg:3.12.0",
        "com.twelvemonkeys.imageio:imageio-webp:3.12.0",
        "org.apache.commons:commons-imaging:1.0.0-alpha5",
        "org.bytedeco:javacpp:1.5.14",
        "org.bytedeco:ffmpeg:8.1.2-1.5.14"
    )
    if (embedFfmpegNatives) {
        for (classifier in listOf("windows-x86_64", "linux-x86_64", "linux-arm64", "macosx-x86_64", "macosx-arm64")) {
            bundled += "org.bytedeco:javacpp:1.5.14:$classifier"
            bundled += "org.bytedeco:ffmpeg:8.1.2-1.5.14:$classifier"
        }
    }
    for (notation in bundled) {
        add("implementation", notation)
        add("jarJar", notation)
    }
}

neoForge {
    version = mod.dep("neoforge_loader")

    mods {
        register(mod.id) {
            sourceSet(sourceSets.named("main").get())
        }
    }

    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run/$projectName/client")
            jvmArguments.add("-Djava.awt.headless=false")
        }
        register("server") {
            server()
            gameDirectory = rootProject.file("run/$projectName/server")
        }
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    classpath += sourceSets.named("main").get().runtimeClasspath
}

tasks.processResources {
    properties(
        listOf("META-INF/neoforge.mods.toml"),
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "minecraft" to mod.dep("minecraft_range_nf"),
        "loader" to mod.dep("neoforge_loader_range"),
        "neoforge" to mod.dep("neoforge_version_range")
    )
    properties(
        listOf("*.mixins.json"),
        "java" to javaVersion
    )
}

// Collect the jar into build/libs/<modversion>/neoforge/ for convenience.
if (releaseTarget) {
    tasks.register<Copy>("buildAndCollect") {
        group = "build"
        from(tasks.named("jar"))
        into(rootProject.layout.buildDirectory.dir("libs/${mod.version}/$loader"))
        dependsOn("build")
    }
} else {
    tasks.matching { it.name in setOf("jar", "sourcesJar", "javadocJar", "jarJar") }.configureEach {
        enabled = false
    }
}
