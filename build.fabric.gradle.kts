plugins {
    id("net.fabricmc.fabric-loom-remap")
    id("com.modrinth.minotaur")
}

val minecraft = stonecutter.current.version
val javaVersion = property("java_version").toString().toInt()

version = "${property("mod_version")}+$minecraft-fabric"
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
    constants.match("fabric", "fabric", "forge", "neoforge")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

// Loader-specific code lives next to the shared code; keep only Fabric's
sourceSets.all {
    java.exclude("**/forge/**", "**/neoforge/**")
    resources.exclude("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta")
}

val selftest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

// A client that has the test mod but not AnvilFix: AnvilFix's classes are there as a plain library (the test code uses
// them), but without its fabric.mod.json the loader never loads it as a mod, so none of its mixins apply. That's what a
// player without AnvilFix brings to an AnvilFix server.
val vanillaClient: SourceSet by sourceSets.creating {
    val main = sourceSets.main.get()
    runtimeClasspath = selftest.output + main.output.classesDirs +
        main.runtimeClasspath.filter { it != main.output.resourcesDir && it !in main.output.classesDirs }
}

repositories {
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api")}")
}

loom {
    mods {
        register("anvilfix") { sourceSet(sourceSets.main.get()) }
        register("anvilfix_selftest") { sourceSet(selftest) }
    }

    runConfigs.all {
        ideConfigGenerated(false)
        runDir = "../../run"
    }

    runs {
        // Dedicated server that runs the anvil scenarios, writes build/selftest/junit.xml and exits non-zero on failure
        register("selftest") {
            server()
            configName = "Self Test"
            source(selftest)
            vmArg("-Danvilfix.selftest=true")
            vmArg("-Danvilfix.host=$hostTest")
            vmArg("-Danvilfix.selftest.report=${layout.buildDirectory.file("selftest/junit.xml").get().asFile}")
            programArg("--nogui")
            runDir = "build/selftest-run"
        }
        // scripts/verify.sh with CLIENT_TASK=runVanillaClient: the in-game test from a client without AnvilFix
        register("vanillaClient") {
            client()
            configName = "Client Without AnvilFix"
            source(vanillaClient)
            vmArg("-Danvilfix.smoke=true")
            vmArg("-Danvilfix.vanillaClient=true")
            runDir = clientDir.relativeTo(projectDir).path
            if (ingame) vmArg("-Danvilfix.ingame=true")
            vmArg("-Danvilfix.port=$testPort")
        }
        // Real client: checks the client mixins apply, then quits
        register("clientSmoke") {
            client()
            configName = "Client Smoke Test"
            source(selftest)
            vmArg("-Danvilfix.smoke=true")
            runDir = clientDir.relativeTo(projectDir).path
            if (ingame) vmArg("-Danvilfix.ingame=true")
            vmArg("-Danvilfix.port=$testPort")
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

tasks.processResources {
    val props = mapOf("version" to project.version, "minecraft" to (findProperty("minecraft_range") ?: minecraft))
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
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
    archiveClassifier = "selftest-dev"
    from(selftest.output)
    exclude("anvilfix_selftest.mixins.json", "com/googlerooeric/anvilfix/selftest/mixin/**")
    filesMatching(listOf("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml")) {
        // drop the test mod's mixin config, including NeoForge's [[mixins]] table header
        filter { line -> if (line.contains("anvilfix_selftest.mixins.json") || line.trim() == "[[mixins]]") "" else line }
    }
}

val remapSelftestJar by tasks.registering(net.fabricmc.loom.task.RemapJarTask::class) {
    archiveClassifier = "selftest"
    inputFile = selftestJar.flatMap { it.archiveFile }
}

// Upload to Modrinth: MODRINTH_TOKEN=... ./gradlew modrinth (every node) or :<node>:modrinth. Nothing is uploaded
// without the token.
modrinth {
    token = providers.environmentVariable("MODRINTH_TOKEN")
    projectId = "anvilfix"
    versionNumber = project.version.toString()
    versionName = "AnvilFix ${property("mod_version")} (Fabric $minecraft)"
    versionType = "release"
    uploadFile.set(tasks.remapJar)
    gameVersions.add(minecraft)
    loaders.add("fabric")
    // This version's section of CHANGELOG.md
    changelog = rootProject.file("CHANGELOG.md").readText()
        .substringAfter("## ${property("mod_version")}\n").substringBefore("\n## ").trim()
    dependencies {
        required.project("fabric-api")
    }
}
