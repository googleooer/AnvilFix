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
            loader == "forge" && stonecutter.eval(version, "<=1.20.1") -> "build.forge-legacy.gradle.kts"
            else -> "build.$loader.gradle.kts"
        }
        fun mc(version: String, vararg loaders: String) = loaders.forEach { loader ->
            version("$version-$loader", version).buildscript(script(version, loader))
        }

        mc("1.19", "fabric", "forge")
        mc("1.19.1", "fabric", "forge")
        mc("1.19.2", "fabric", "forge")

        vcsVersion = "1.19-fabric"
    }
}

rootProject.name = "AnvilFix"
