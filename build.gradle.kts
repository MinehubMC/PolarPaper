plugins {
    java
    `maven-publish`

    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.run)
    alias(libs.plugins.resource.paper)
}

group = "live.minehub"
version = "1.1.1"

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
                name = "GithubPackages"
                url = uri("https://maven.pkg.github.com/MinehubMC/PolarPaper")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
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

paperPluginYaml {
    name = project.name
    version = project.version.toString()
    description = "Polar world format for Paper"
    apiVersion = "1.21"

    main = "live.minehub.polarpaper.PaperPolar"
    loader = "live.minehub.polarpaper.PaperPolarLoader"
}