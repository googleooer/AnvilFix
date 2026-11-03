// Forge 1.19 - 1.20.1, through ModDevGradle's legacy plugin (these versions use SRG names at runtime)
plugins {
    id("net.neoforged.moddev.legacyforge")
}

val minecraft = stonecutter.current.version
val javaVersion = property("java_version").toString().toInt()

version = "${property("mod_version")}+$minecraft-forge"
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
}

val selftest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

legacyForge {
    // Skip decompiling and recompiling Minecraft: much faster setup, and nothing here needs the game's sources
    enable {
        forgeVersion = "$minecraft-${property("forge_version")}"
        isDisableRecompilation = true
    }

    mods {
        register("anvilfix") { sourceSet(sourceSets.main.get()) }
        register("anvilfix_selftest") { sourceSet(selftest) }
    }

    runs {
        register("selftest") {
            server()
            sourceSet = selftest
            systemProperty("anvilfix.selftest", "true")
            systemProperty("anvilfix.host", hostTest.toString())
            systemProperty("anvilfix.selftest.report", layout.buildDirectory.file("selftest/junit.xml").get().asFile.path)
            programArgument("--nogui")
            gameDirectory = layout.buildDirectory.dir("selftest-run").get().asFile
        }
        register("clientSmoke") {
            client()
            sourceSet = selftest
            systemProperty("anvilfix.smoke", "true")
            if (ingame) systemProperty("anvilfix.ingame", "true")
            systemProperty("anvilfix.port", testPort)
            gameDirectory = clientDir
        }
    }
}

mixin {
    add(sourceSets.main.get(), "anvilfix.refmap.json")
    config("anvilfix.mixins.json")
    config("anvilfix_selftest.mixins.json")
}

dependencies {
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
}

tasks.named("runSelftest") {
    doFirst {
        val dir = layout.buildDirectory.dir("selftest-run").get().asFile
        dir.mkdirs()
        dir.resolve("eula.txt").writeText("eula=true\n")
        dir.resolve("server.properties").writeText("online-mode=false\nwhite-list=false\nenforce-secure-profile=false\nserver-port=$testPort\nspawn-protection=0\nmax-tick-time=-1\nlevel-type=flat\ngenerate-structures=false\n")
    }
}

tasks.jar {
    manifest.attributes("MixinConfigs" to "anvilfix.mixins.json")
}

val metadata = mapOf(
    "version" to project.version.toString(),
    "minecraft" to (findProperty("minecraft_range") ?: "[$minecraft]"),
    "loader" to property("forge_loader_range").toString(),
    // pack.mcmeta switched from pack_format to a min/max range in 1.21.9
    "pack_format_fields" to property("pack_format").toString().let {
        if (stonecutter.eval(minecraft, ">=1.21.9")) "\"min_format\": $it, \"max_format\": $it" else "\"pack_format\": $it"
    }
)

tasks.processResources {
    inputs.properties(metadata)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(metadata) }
    // Point the mixin config at the refmap the annotation processor writes
    filesMatching("anvilfix.mixins.json") { filter { it.replace("\"package\"", "\"refmap\": \"anvilfix.refmap.json\",\n  \"package\"") } }
}

tasks.named<ProcessResources>("processSelftestResources") {
    inputs.properties(metadata)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) { expand(metadata) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
}
