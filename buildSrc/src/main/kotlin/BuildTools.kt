import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

// Accessor to retrieve mod configuration properties defined in gradle.properties.
val Project.mod: ModData get() = ModData(this)

// Helper to retrieve a property from gradle.properties as a String.
fun Project.prop(key: String): String? = findProperty(key)?.toString()

// Replaces tokens in resource files (e.g. fabric.mod.json, neoforge.mods.toml) during build time.
fun ProcessResources.properties(files: Iterable<String>, vararg properties: Pair<String, Any>) {
    for ((name, value) in properties) inputs.property(name, value)
    filesMatching(files) {
        expand(properties.toMap())
    }
}

/**
 * Walks the given source roots (e.g. src/common/src/main/java and the active loader's
 * src tree), applies the [Preprocessor] to strip/transform version-conditional code based
 * on the active target Minecraft version, and registers the generated folder as the main
 * java source directory.
 *
 * This is the cross-loader source-merge step that Architectury Loom would normally provide;
 * here it is done explicitly so we can stay on the native Fabric/NeoForge toolchains.
 */
fun Project.versionedJavaSources(vararg roots: File) {
    val generatedSources = layout.buildDirectory.dir("generated/preprocessed/main")

    val prepareSources = tasks.register("prepareVersionedJavaSources") {
        inputs.files(roots.toList())
        outputs.dir(generatedSources)
        dependsOn(tasks.matching { it.name == "stonecutterGenerate" })

        doLast {
            val outputRoot = generatedSources.get().asFile
            outputRoot.deleteRecursively()

            // Target Minecraft version is the subproject name without the loader suffix
            // (e.g. "26.1-fabric" -> "26.1").
            val version = project.name.substringBeforeLast('-')

            for (root in roots) {
                if (!root.exists()) continue
                root.walkTopDown()
                    .filter { it.isFile && it.extension == "java" }
                    .forEach { file ->
                        val relative = root.toPath().relativize(file.toPath())
                        val output = outputRoot.toPath().resolve(relative).toFile()
                        output.parentFile.mkdirs()
                        output.writeText(Preprocessor.transform(file.readLines(), version))
                    }
            }
        }
    }

    val active = isStonecutterProjectActive()
    extensions.getByType<SourceSetContainer>().named("main") {
        // During IDE sync of the active target, point at the real sources so navigation/editing works.
        // For builds (and inactive targets), compile the preprocessed output.
        if (System.getProperty("idea.sync.active") == "true" && active) {
            java.setSrcDirs(roots.toList())
        } else {
            java.setSrcDirs(listOf(generatedSources))
        }
    }

    tasks.named("compileJava") {
        dependsOn(prepareSources)
        dependsOn(tasks.matching { it.name == "stonecutterGenerate" })
    }
}

// Structured accessor for mod metadata and dependency versions from gradle.properties.
@JvmInline
value class ModData(private val project: Project) {
    val id: String get() = requireNotNull(project.prop("mod.id")) { "Missing 'mod.id' in gradle.properties" }
    val name: String get() = requireNotNull(project.prop("mod.name")) { "Missing 'mod.name' in gradle.properties" }
    val version: String get() = requireNotNull(project.prop("mod.version")) { "Missing 'mod.version' in gradle.properties" }
    val group: String get() = requireNotNull(project.prop("mod.group")) { "Missing 'mod.group' in gradle.properties" }

    fun prop(key: String) = requireNotNull(project.prop("mod.$key")) { "Missing 'mod.$key' in gradle.properties" }
    fun dep(key: String) = requireNotNull(project.prop("dep.$key")) { "Missing 'dep.$key' in gradle.properties" }
}

private fun Project.isStonecutterProjectActive(): Boolean {
    val stonecutter = extensions.findByName("stonecutter") ?: return false
    return try {
        val current = stonecutter.javaClass.getMethod("getCurrent").invoke(stonecutter)
        current.javaClass.getMethod("isActive").invoke(current) as? Boolean ?: false
    } catch (e: Exception) {
        false
    }
}
