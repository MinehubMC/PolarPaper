plugins {
    java
    `maven-publish`

    alias(libs.plugins.shadow)
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.run)
}

group = "live.minehub"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("${libs.versions.minecraft.get()}-R0.1-SNAPSHOT")

    implementation(libs.zstd)
    implementation(libs.adventure.nbt)
}

tasks {
    assemble {
        dependsOn(shadowJar, reobfJar)
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