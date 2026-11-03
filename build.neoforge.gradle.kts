// NeoForge 1.20.2+, through ModDevGradle (NeoForge uses Mojang names at runtime, so no refmaps)
plugins {
    id("net.neoforged.moddev")
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
    version = property("neoforge_version").toString()

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
            gameDirectory = rootProject.file("run")
        }
    }
}

tasks.named("runSelftest") {
    doFirst {
        val dir = layout.buildDirectory.dir("selftest-run").get().asFile
        dir.mkdirs()
        dir.resolve("eula.txt").writeText("eula=true\n")
        dir.resolve("server.properties").writeText("online-mode=false\nwhite-list=false\nenforce-secure-profile=false\nserver-port=$testPort\nspawn-protection=0\nmax-tick-time=-1\n")
    }
}

val metadata = mapOf(
    "version" to project.version.toString(),
    "minecraft" to (findProperty("minecraft_range") ?: "[$minecraft]"),
    "neoforge" to property("neoforge_version").toString(),
    "pack_format" to property("pack_format").toString(),
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
