// Forge 1.20.2+, through ForgeGradle 7 (Mojang names at runtime, so no refmaps or reobfuscation)
plugins {
    // ForgeGradle registers Eclipse run configurations next to every run task
    eclipse
    id("net.minecraftforge.gradle")
}

val mcVersion = stonecutter.current.version
val javaVersion = property("java_version").toString().toInt()

version = "${property("mod_version")}+$mcVersion-forge"
group = property("maven_group").toString()
base.archivesName = property("archives_base_name").toString()

// -Panvilfix.host=true keeps the self-test server up for the in-game test client, which -Panvilfix.ingame=true
// connects to it (see scripts/verify.sh)
val hostTest = findProperty("anvilfix.host") == "true"
val ingame = findProperty("anvilfix.ingame") == "true"
val testPort = findProperty("anvilfix.port")?.toString() ?: "25599"
// Each parallel verify lane runs its client in its own game directory
val clientDir = findProperty("anvilfix.clientDir")?.toString()?.let { file(it) } ?: rootProject.file("run")

stonecutter {
    constants.match("forge", "fabric", "forge", "neoforge")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}

sourceSets.all {
    java.exclude("**/fabric/**", "**/neoforge/**")
    resources.exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
    // Forge finds dev mods by directory, so classes and resources must land in the same one (as in the Forge MDK)
    val dir = layout.buildDirectory.dir("sourceSets/$name")
    output.setResourcesDir(dir)
    java.destinationDirectory.set(dir)
}

val selftest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
// ForgeGradle only adds run tasks to source sets that depend on Minecraft through their own configurations
configurations[selftest.implementationConfigurationName].extendsFrom(configurations.implementation.get())

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
}

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:$mcVersion-${property("forge_version")}"))
}

minecraft {
    mappings("official", mcVersion)
    runs {
        configureEach {
            // Older FML only finds dev mods listed as modid%%path, so name each mod and its source set
            mods {
                register("anvilfix") { source(sourceSets.main.get()) }
                register("anvilfix_selftest") { source(selftest) }
            }
            // In dev there's no jar manifest to list the mixin configs, so pass them like ForgeGradle 6 did
            args("--mixin.config", "anvilfix.mixins.json", "--mixin.config", "anvilfix_selftest.mixins.json")
            // Forge before 1.20.6 reads them from MOD_CLASSES, which its run configs leave to the build tool to set
            if (stonecutter.eval(mcVersion, "<1.20.6")) {
                environment("MOD_CLASSES", provider {
                    listOf("anvilfix" to sourceSets.main.get(), "anvilfix_selftest" to selftest).flatMap { (id, set) ->
                        (set.output.classesDirs.files + listOfNotNull(set.output.resourcesDir)).map { "$id%%${it.absolutePath}" }
                    }.joinToString(File.pathSeparator)
                })
            }
        }
        register("server") {
            args("--nogui")
            systemProperty("anvilfix.selftest", "true")
            systemProperty("anvilfix.host", hostTest.toString())
            systemProperty("anvilfix.selftest.report", layout.buildDirectory.file("selftest/junit.xml").get().asFile.path)
            workingDir.set(layout.buildDirectory.dir("selftest-run"))
        }
        register("client") {
            // GLFW on macOS needs the main thread; newer Forge run configs add this themselves, older ones don't
            if (System.getProperty("os.name").startsWith("Mac") && stonecutter.eval(mcVersion, "<1.20.6")) {
                jvmArgs("-XstartOnFirstThread")
            }
            systemProperty("anvilfix.smoke", "true")
            if (ingame) systemProperty("anvilfix.ingame", "true")
            systemProperty("anvilfix.port", testPort)
            workingDir.set(clientDir)
        }
    }
}

// ForgeGradle names run tasks per source set; keep the task names the other loaders use
tasks.register("runSelftest") {
    dependsOn("runSelftestServer")
}
tasks.register("runClientSmoke") {
    dependsOn("runSelftestClient")
}
tasks.matching { it.name == "runSelftestServer" }.configureEach {
    doFirst {
        val dir = layout.buildDirectory.dir("selftest-run").get().asFile
        dir.mkdirs()
        dir.resolve("eula.txt").writeText("eula=true\n")
        dir.resolve("server.properties").writeText("online-mode=false\nwhite-list=false\nenforce-secure-profile=false\nserver-port=$testPort\nspawn-protection=0\nmax-tick-time=-1\nlevel-type=flat\ngenerate-structures=false\n")
    }
}

val metadata = mapOf(
    "version" to project.version.toString(),
    "minecraft" to (findProperty("minecraft_range") ?: "[$mcVersion]"),
    "loader" to property("forge_loader_range").toString(),
    // pack.mcmeta switched from pack_format to a min/max range in 1.21.9
    "pack_format_fields" to property("pack_format").toString().let {
        if (stonecutter.eval(mcVersion, ">=1.21.9")) "\"min_format\": $it, \"max_format\": $it" else "\"pack_format\": $it"
    }
)

tasks.processResources {
    inputs.properties(metadata)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(metadata) }
}

tasks.named<ProcessResources>("processSelftestResources") {
    inputs.properties(metadata)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(metadata) }
}

tasks.jar {
    manifest.attributes("MixinConfigs" to "anvilfix.mixins.json")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
}
