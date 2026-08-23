plugins {
    id("fabric-loom") version "1.10.5"
    `maven-publish`
}

group = property("maven_group") as String
version = property("mod_version") as String

base { archivesName.set(property("archives_base_name") as String) }

repositories {
    maven("https://maven.plasmovoice.com/releases")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")
    modCompileOnly("su.plo.voice.client:plasmovoice-api:${property("plasmo_voice_api_version")}")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

java {
    withSourcesJar()
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") { expand("version" to project.version) }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
