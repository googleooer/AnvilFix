// NeoForge releases that ModDevGradle can't consume because they publish no moddev bundle (1.20.2, 1.20.3, 1.20.5),
// through NeoGradle instead. Same layout and tasks as build.neoforge.gradle.kts.
plugins {
    id("net.neoforged.gradle.userdev")
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

// NeoGradle runs its own tools (e.g. JST, built for Java 21) on the project toolchain, so use at least 21 and
// still target the game's Java version through --release
java {
    toolchain.languageVersion = JavaLanguageVersion.of(maxOf(javaVersion, 21))
}

val metadataFile = if (stonecutter.eval(minecraft, ">=1.20.5")) "neoforge.mods.toml" else "mods.toml"

sourceSets.all {
    java.exclude("**/fabric/**", "**/forge/**")
    resources.exclude("fabric.mod.json", "META-INF/mods.toml")
}

val selftest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

// Each source set is its own mod at runtime
sourceSets.main.get().extensions.getByType(net.neoforged.gradle.dsl.common.extensions.sourceset.RunnableSourceSet::class.java).modIdentifier.set("anvilfix")
selftest.extensions.getByType(net.neoforged.gradle.dsl.common.extensions.sourceset.RunnableSourceSet::class.java).modIdentifier.set("anvilfix_selftest")

dependencies {
    implementation("net.neoforged:neoforge:${property("neoforge_version")}")
}

runs {
    register("selftest") {
        runType("server")
        modSources(sourceSets.main.get(), selftest)
        systemProperties.put("anvilfix.selftest", "true")
        systemProperties.put("anvilfix.host", hostTest.toString())
        systemProperties.put("anvilfix.selftest.report", layout.buildDirectory.file("selftest/junit.xml").get().asFile.path)
        arguments.add("--nogui")
        workingDirectory.set(layout.buildDirectory.dir("selftest-run"))
    }
    register("clientSmoke") {
        runType("client")
        modSources(sourceSets.main.get(), selftest)
        systemProperties.put("anvilfix.smoke", "true")
        if (ingame) systemProperties.put("anvilfix.ingame", "true")
        systemProperties.put("anvilfix.port", testPort)
        workingDirectory.set(clientDir)
    }
}

// NeoGradle creates its run tasks late
tasks.matching { it.name == "runSelftest" }.configureEach {
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
    // FML before NeoForge 20.4 only understands the old "mandatory" dependency flag, and later ones reject it
    "required" to if (stonecutter.eval(minecraft, "<1.20.4")) "mandatory = true" else "type = \"required\""
)

tasks.processResources {
    inputs.properties(metadata)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta")) { expand(metadata) }
    rename("neoforge\\.mods\\.toml", metadataFile)
}

tasks.named<ProcessResources>("processSelftestResources") {
    inputs.properties(metadata)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta")) { expand(metadata) }
    rename("neoforge\\.mods\\.toml", metadataFile)
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
        filter { line -> if (line.contains("anvilfix_selftest.mixins.json")) "" else line }
    }
}
