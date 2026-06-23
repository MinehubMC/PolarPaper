plugins {
    java
    `maven-publish`

    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.run)
    alias(libs.plugins.resource.paper)
    alias(libs.plugins.hangar.publish)
    alias(libs.plugins.shadow)
}

val developmentVersion = "2.1.1"

version = getVersion()
group = "live.minehub"

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("${libs.versions.minecraft.get()}.build.+")

    implementation(project(":core"))
    implementation(project(":paper_latest"))
    implementation(project(":paper_26_1_2"))
    implementation(project(":paper_1_21_11"))
    compileOnly(libs.zstd)
}

tasks {
    runPaper.folia.registerTask()
    runServer {
//        minecraftVersion(libs.versions.minecraft.get())
        minecraftVersion("1.21.11")
//        minecraftVersion("26.1.2")
    }

    shadowJar {
        archiveClassifier.set("")
    }

    jar {
        enabled = false
    }
}

allprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(25))
            }
        }
    }
}

java {
    // Generate sources JAR
    withSourcesJar()
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
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
            from(components["shadow"])
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
    apiVersion = libs.versions.minecraft.get()

    foliaSupported = true

    main = "live.minehub.polarpaper.PolarPaper"
    loader = "live.minehub.polarpaper.PolarPaperLoader"
}

hangarPublish {
    publications.register("plugin") {
        version = project.version as String // use project version as publication version
        id = "PolarPaper"
        channel = "Development"

        // your api key.
        // defaults to the `io.papermc.hangar-publish-plugin.[publicationName].api-key` or `io.papermc.hangar-publish-plugin.default-api-key` Gradle properties
        apiKey = System.getenv("HANGAR_API_KEY")

        // register platforms
        platforms {
            paper {
                jar = tasks.jar.flatMap { it.archiveFile }
                platformVersions = listOf(libs.versions.minecraft.get())
            }
        }
    }
}