plugins {
    id("java")
    id("net.fabricmc.fabric-loom-remap") version "1.16-SNAPSHOT"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

    // Compile-time only: these are provided at runtime by Vibrancy / Sodium/ Big Shot Lib themselves,
    // since this is a thin add-on that only patches Vibrancy's behaviour.
    //
    // NOTE: fill in the exact version numbers in gradle.properties yourself before building.
    // All three are published on Modrinth, so the "version number" (not the display title)
    // shown on each mod's Modrinth versions page (filtered to Fabric + 1.21.11) is what goes here:
    //   - https://modrinth.com/mod/vibrancy/versions?l=fabric&g=1.21.11
    //   - https://modrinth.com/mod/sodium/versions?l=fabric&g=1.21.11
    //   - https://modrinth.com/mod/big-shot-lib/versions?l=fabric&g=1.21.11
    modCompileOnly("maven.modrinth:vibrancy:${project.property("vibrancy_version")}")
    modCompileOnly("maven.modrinth:sodium:${project.property("sodium_version")}")
    modCompileOnly("maven.modrinth:big-shot-lib:${project.property("big_shot_lib_version")}")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft_version" to project.property("minecraft_version"),
        "loader_version" to project.property("loader_version")
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.property("archives_base_name")}" }
    }
}
