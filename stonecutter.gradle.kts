plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom-remap") version "1.19.3" apply false
    id("net.neoforged.moddev.legacyforge") version "2.0.148" apply false
    id("net.neoforged.moddev") version "2.0.148" apply false
    id("net.neoforged.gradle.userdev") version "7.1.39" apply false
    id("net.minecraftforge.gradle") version "7.0.40" apply false
}

stonecutter active "1.19-fabric"
