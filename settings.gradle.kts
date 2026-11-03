pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.kikugie.dev/releases")
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        // Each node is "<minecraft version>-<loader>". Forge up to 1.20.1 uses SRG names at runtime and needs
        // ModDevGradle's legacy plugin, so it gets its own build script.
        fun script(version: String, loader: String) = when {
            // 26.x ships unobfuscated: Fabric uses the non-remapping Loom
            loader == "fabric" && stonecutter.eval(version, ">=26.1") -> "build.fabric-unobf.gradle.kts"
            loader == "forge" && stonecutter.eval(version, "<=1.20.1") -> "build.forge-legacy.gradle.kts"
            // These NeoForge releases publish no ModDevGradle bundle
            loader == "neoforge" && version in setOf("1.20.2", "1.20.3", "1.20.5") -> "build.neoforge-ng.gradle.kts"
            else -> "build.$loader.gradle.kts"
        }
        fun mc(version: String, vararg loaders: String) = loaders.forEach { loader ->
            version("$version-$loader", version).buildscript(script(version, loader))
        }

        mc("1.19", "fabric", "forge")
        mc("1.19.1", "fabric", "forge")
        mc("1.19.2", "fabric", "forge")
        mc("1.19.3", "fabric", "forge")
        mc("1.19.4", "fabric", "forge")
        mc("1.20", "fabric", "forge")
        mc("1.20.1", "fabric", "forge")
        mc("1.20.2", "fabric", "forge", "neoforge")
        mc("1.20.3", "fabric", "forge", "neoforge")
        mc("1.20.4", "fabric", "forge", "neoforge")
        mc("1.20.5", "fabric")
        mc("1.20.6", "fabric")
        mc("1.21", "fabric")
        mc("1.21.1", "fabric")
        mc("1.21.2", "fabric")
        mc("1.21.3", "fabric")
        mc("1.21.4", "fabric")
        mc("1.21.5", "fabric")
        mc("1.21.6", "fabric")
        mc("1.21.7", "fabric")
        mc("1.21.8", "fabric")
        mc("1.21.9", "fabric")
        mc("1.21.10", "fabric")
        mc("1.21.11", "fabric")
        mc("26.1", "fabric")
        mc("26.1.1", "fabric")
        mc("26.1.2", "fabric")
        mc("26.2", "fabric")
        mc("26.3", "fabric")

        vcsVersion = "1.19-fabric"
    }
}

rootProject.name = "AnvilFix"
