import java.util.zip.GZIPInputStream

// Forge 1.20.2+, through ForgeGradle 7 (Mojang names at runtime, so no refmaps or reobfuscation)
plugins {
    // ForgeGradle registers Eclipse run configurations next to every run task
    eclipse
    id("net.minecraftforge.gradle")
    id("com.modrinth.minotaur")
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
// Forge before 1.20.6 runs on SRG names in production (see the reobf section below)
val srgRuntime = stonecutter.eval(mcVersion, "<1.20.6")
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

// force_dependencies pins library versions per node. Forge 1.21 needs it: Gradle upgrades its jopt-simple to
// 6.0-alpha-3, whose module name modlauncher can't find ("Module jopt.simple not found").
findProperty("force_dependencies")?.toString()?.split(",")?.map { it.trim() }?.let { pins ->
    configurations.all { resolutionStrategy.force(*pins.toTypedArray()) }
}

val forgeDependency = minecraft.dependency("net.minecraftforge:forge:$mcVersion-${property("forge_version")}")

dependencies {
    implementation(forgeDependency)
}

// Forge before 1.20.6 still runs on SRG names in production, and ForgeGradle 7 leaves reobfuscation to the build
// script: give the mixins an SRG refmap and remap the jar with ForgeAutoRenamingTool, like ForgeGradle 6 did.
if (srgRuntime) {
    val reobfDir = layout.buildDirectory.dir("reobf")
    val mapFile = reobfDir.map { it.file("map2srg.tsrg") }
    val refmapFile = reobfDir.map { it.file("anvilfix.refmap.json") }

    // ForgeGradle hands out tsrg2; the 0.8.5 Mixin processor reads tsrg v1, which is tsrg2 minus its header and the
    // doubly-indented static/parameter lines
    val srgMappings by tasks.registering {
        val source = forgeDependency.toSrgFile
        inputs.file(source)
        outputs.file(mapFile)
        doLast {
            val lines = GZIPInputStream(source.get().inputStream()).bufferedReader().readLines()
            mapFile.get().asFile.writeText(lines.drop(1).filterNot { it.startsWith("\t\t") }.joinToString("\n", postfix = "\n"))
        }
    }

    val art by configurations.creating
    dependencies {
        annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
        art("net.minecraftforge:ForgeAutoRenamingTool:1.1.2:all") { isTransitive = false }
    }

    tasks.compileJava {
        dependsOn(srgMappings)
        options.compilerArgs.addAll(listOf(
            "-AreobfTsrgFile=${mapFile.get().asFile}",
            "-AoutRefMapFile=${refmapFile.get().asFile}",
            "-AmappingTypes=tsrg",
            "-AdefaultObfuscationEnv=searge",
        ))
    }

    tasks.processResources {
        // Gradle doesn't see filter lambdas, so declare the change for up-to-date checks
        inputs.property("srgRefmap", true)
        filesMatching("anvilfix.mixins.json") { filter { it.replace("\"package\"", "\"refmap\": \"anvilfix.refmap.json\",\n  \"package\"") } }
    }

    // The Mojang-named jar goes to devlibs; the SRG one becomes the release jar
    tasks.jar {
        dependsOn(tasks.compileJava)
        from(refmapFile)
        destinationDirectory = layout.buildDirectory.dir("devlibs")
    }

    val gameJava = javaVersion
    val reobfJar by tasks.registering(JavaExec::class) {
        val input = tasks.jar.flatMap { it.archiveFile }
        val output = layout.buildDirectory.file("libs/${base.archivesName.get()}-${project.version}.jar")
        inputs.file(input)
        inputs.file(mapFile)
        outputs.file(output)
        dependsOn(srgMappings)
        classpath = art
        mainClass = "net.minecraftforge.fart.Main"
        javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(gameJava)) })
        argumentProviders.add(CommandLineArgumentProvider {
            listOf("--input", input.get().asFile.path, "--output", output.get().asFile.path, "--map", mapFile.get().asFile.path) +
                sourceSets.main.get().compileClasspath.files.flatMap { listOf("--lib", it.path) }
        })
    }
    tasks.assemble { dependsOn(reobfJar) }

    // The production test mod needs the same remapping (scripts/prod-test.sh)
    tasks.register<JavaExec>("reobfSelftestJar") {
        val input = tasks.named<Jar>("selftestJar").flatMap { it.archiveFile }
        val output = layout.buildDirectory.file("libs/${base.archivesName.get()}-${project.version}-selftest.jar")
        inputs.file(input)
        inputs.file(mapFile)
        outputs.file(output)
        dependsOn(srgMappings)
        classpath = art
        mainClass = "net.minecraftforge.fart.Main"
        javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(gameJava)) })
        argumentProviders.add(CommandLineArgumentProvider {
            listOf("--input", input.get().asFile.path, "--output", output.get().asFile.path, "--map", mapFile.get().asFile.path) +
                (sourceSets.main.get().compileClasspath.files + sourceSets.main.get().output.files).flatMap { listOf("--lib", it.path) }
        })
    }
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
            // The SRG refmap is for production; dev runs use Mojang names directly
            if (srgRuntime) systemProperty("mixin.env.disableRefMap", "true")
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
            // GLFW on macOS needs the main thread, and Forge's run configs don't ask for it
            if (System.getProperty("os.name").startsWith("Mac")) {
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
        dir.resolve("server.properties").writeText("online-mode=false\nwhite-list=false\nenforce-secure-profile=false\nserver-port=$testPort\nspawn-protection=0\nmax-tick-time=-1\nlevel-type=minecraft:flat\ngenerate-structures=false\n")
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

// LGPL-3.0: ship the license (and the GPL it builds on) inside the jar
tasks.jar {
    from(rootProject.files("LICENSE", "COPYING")) { into("META-INF") }
}

// Production build of the test mod for scripts/prod-test.sh: same scenarios, without its own mixins (those only
// matter for the dev-side checks and would need their own refmaps here)
val selftestJar by tasks.registering(Jar::class) {
    archiveClassifier = if (srgRuntime) "selftest-dev" else "selftest"
    from(selftest.output)
    exclude("anvilfix_selftest.mixins.json", "com/googlerooeric/anvilfix/selftest/mixin/**")
    filesMatching(listOf("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml")) {
        // drop the test mod's mixin config, including NeoForge's [[mixins]] table header
        filter { line -> if (line.contains("anvilfix_selftest.mixins.json") || line.trim() == "[[mixins]]") "" else line }
    }
}

// Upload to Modrinth: MODRINTH_TOKEN=... ./gradlew modrinth (every node) or :<node>:modrinth. Nothing is uploaded
// without the token.
modrinth {
    token = providers.environmentVariable("MODRINTH_TOKEN")
    projectId = "anvilfix"
    versionNumber = project.version.toString()
    versionName = "AnvilFix ${property("mod_version")} (Forge $mcVersion)"
    versionType = "release"
    uploadFile.set(if (srgRuntime) tasks.named("reobfJar") else tasks.jar)
    gameVersions.add(mcVersion)
    loaders.add("forge")
    // This version's section of CHANGELOG.md
    changelog = rootProject.file("CHANGELOG.md").readText()
        .substringAfter("## ${property("mod_version")}\n").substringBefore("\n## ").trim()
}
