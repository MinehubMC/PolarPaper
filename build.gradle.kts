plugins {
    java
    `maven-publish`

    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.run)
    alias(libs.plugins.resource.paper)
}

val developmentVersion = "1.24.4.5"

version = getVersion()
group = "live.minehub"

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("${libs.versions.minecraft.get()}-R0.1-SNAPSHOT")

    compileOnly(libs.zstd)
    compileOnly(libs.adventure.nbt)
}

tasks {
    assemble {
        dependsOn(reobfJar)
    }

    runServer {
        minecraftVersion(libs.versions.minecraft.get())
    }
}

publishing {
    repositories {
        // Publish to Maven Local only if not running in an action environment
        if (!isAction()) {
            mavenLocal()
        } else {
            maven {
                name = if (version.toString().endsWith("-SNAPSHOT")) "Snapshots" else "Releases"
                url = uri("https://repo.minehub.live/" + if (version.toString().endsWith("-SNAPSHOT")) "snapshots" else "releases")
                credentials {
                    username = System.getenv("REPO_ACTOR")
                    password = System.getenv("REPO_TOKEN")
                }
            }
        }
    }

    publications {
        create<MavenPublication>("shadow") {
            from(components["java"])
        }
    }
}

fun isAction(): Boolean {
    return System.getenv("CI") != null
}

fun getVersion(): String {
    return if (!isAction()) {
        developmentVersion
    } else {
        project.findProperty("version") as String
    }
}

paperPluginYaml {
    name = project.name
    version = project.version.toString()
    description = "Polar world format for Paper"
    apiVersion = "1.21"

    main = "live.minehub.polarpaper.PolarPaper"
    loader = "live.minehub.polarpaper.PolarPaperLoader"
}