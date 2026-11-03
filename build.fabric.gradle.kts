plugins {
    id("net.fabricmc.fabric-loom-remap")
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
        // Real client: checks the client mixins apply, then quits
        register("clientSmoke") {
            client()
            configName = "Client Smoke Test"
            source(selftest)
            vmArg("-Danvilfix.smoke=true")
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
        dir.resolve("server.properties").writeText("online-mode=false\nwhite-list=false\nenforce-secure-profile=false\nserver-port=$testPort\nspawn-protection=0\nmax-tick-time=-1\n")
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
