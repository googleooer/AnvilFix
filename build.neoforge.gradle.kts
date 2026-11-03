// NeoForge 1.20.2+, through ModDevGradle (NeoForge uses Mojang names at runtime, so no refmaps)
plugins {
    id("net.neoforged.moddev")
    id("com.modrinth.minotaur")
}

val minecraft = stonecutter.current.version
val javaVersion = property("java_version").toString().toInt()

version = "${property("mod_version")}+$minecraft-neoforge"
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
    constants.match("neoforge", "fabric", "forge", "neoforge")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}

// Before 1.20.5 NeoForge still reads META-INF/mods.toml
val metadataFile = if (stonecutter.eval(minecraft, ">=1.20.5")) "META-INF/neoforge.mods.toml" else "META-INF/mods.toml"

sourceSets.all {
    java.exclude("**/fabric/**", "**/forge/**")
    resources.exclude("fabric.mod.json", "META-INF/mods.toml")
}

val selftest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

neoForge {
    // Skip decompiling and recompiling Minecraft: much faster setup, and nothing here needs the game's sources
    enable {
        version = property("neoforge_version").toString()
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

tasks.named("runSelftest") {
    doFirst {
        val dir = layout.buildDirectory.dir("selftest-run").get().asFile
        dir.mkdirs()
        dir.resolve("eula.txt").writeText("eula=true\n")
        dir.resolve("server.properties").writeText("online-mode=false\nwhite-list=false\nenforce-secure-profile=false\nserver-port=$testPort\nspawn-protection=0\nmax-tick-time=-1\nlevel-type=minecraft:flat\ngenerate-structures=false\n")
    }
}

val metadata = mapOf(
    "version" to project.version.toString(),
    "minecraft" to (findProperty("minecraft_range") ?: "[$minecraft]"),
    "neoforge" to property("neoforge_version").toString(),
    // pack.mcmeta switched from pack_format to a min/max range in 1.21.9
    "pack_format_fields" to property("pack_format").toString().let {
        if (stonecutter.eval(minecraft, ">=1.21.9")) "\"min_format\": $it, \"max_format\": $it" else "\"pack_format\": $it"
    },
    // NeoForge 26.2 warns about logoFile and wants iconFile for square icons; older versions only know logoFile
    "logo_key" to if (stonecutter.eval(minecraft, ">=26.2")) "iconFile" else "logoFile",
    // FML before NeoForge 20.4 only understands the old "mandatory" dependency flag
    "required" to "type = \"required\""
)

tasks.processResources {
    inputs.properties(metadata)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta")) { expand(metadata) }
    rename("neoforge\\.mods\\.toml", metadataFile.substringAfter("/"))
}

tasks.named<ProcessResources>("processSelftestResources") {
    inputs.properties(metadata)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta")) { expand(metadata) }
    rename("neoforge\\.mods\\.toml", metadataFile.substringAfter("/"))
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
    archiveClassifier = "selftest"
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
    versionName = "AnvilFix ${property("mod_version")} (NeoForge $minecraft)"
    versionType = "release"
    uploadFile.set(tasks.jar)
    gameVersions.add(minecraft)
    loaders.add("neoforge")
    // This version's section of CHANGELOG.md
    changelog = rootProject.file("CHANGELOG.md").readText()
        .substringAfter("## ${property("mod_version")}\n").substringBefore("\n## ").trim()
}
