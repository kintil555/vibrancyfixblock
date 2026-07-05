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
}

/**
 * Vibrancy, Sodium, and Big Shot Lib for 1.21.11 Fabric aren't (yet) all published
 * somewhere Gradle can resolve automatically:
 *  - Vibrancy & Big Shot Lib's 1.21.11 builds aren't on Modrinth's public Maven.
 *  - Sodium is under the Polyform Shield license, which makes casually redistributing
 *    it from a third-party repo something we'd rather not do.
 *
 * So instead: drop the exact jars you're running in-game into ./libs/ (this folder is
 * git-ignored) and we depend on them directly as local files. They're compile-only -
 * none of this gets bundled into the built mod, it's only needed to compile against
 * Vibrancy's classes.
 *
 * Matching is done by filename prefix (case-insensitive) rather than an exact filename,
 * since GitHub may normalize asset names slightly on upload/download.
 *
 * See README.md for how CI obtains these without committing them to the repo.
 */
val libsDir = file("libs")

fun findLocalJar(prefix: String): File {
    val match = libsDir.listFiles { f -> f.name.lowercase().startsWith(prefix.lowercase()) && f.extension == "jar" }
        ?.firstOrNull()
    if (match == null) {
        throw GradleException(
            "[vibrancyfixblock] Missing required local dependency: no file starting with " +
                "'$prefix' found in ${libsDir.path}/\n" +
                "Place the exact Vibrancy / Sodium / Big Shot Lib jars you use in-game into " +
                "the libs/ folder (see README.md -> 'Building it yourself')."
        )
    }
    return match
}

val vibrancyJar = findLocalJar("vibrancy-fabric")
val sodiumJar = findLocalJar("sodium-fabric")
val bigShotLibJar = findLocalJar("big_shot_lib")

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

    // Compile-time only: these are provided at runtime by Vibrancy / Sodium / Big Shot Lib
    // themselves, since this is a thin add-on that only patches Vibrancy's behaviour.
    modCompileOnly(files(vibrancyJar))
    modCompileOnly(files(sodiumJar))
    modCompileOnly(files(bigShotLibJar))
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
