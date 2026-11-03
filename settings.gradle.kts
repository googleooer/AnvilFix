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
        // Each node is "<minecraft version>-<loader>" and builds with build.<loader>.gradle.kts
        fun mc(version: String, vararg loaders: String) = loaders.forEach { loader ->
            version("$version-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        mc("1.19", "fabric")

        vcsVersion = "1.19-fabric"
    }
}

rootProject.name = "AnvilFix"
