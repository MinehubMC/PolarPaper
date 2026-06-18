plugins {
    id("java")

    alias(libs.plugins.paperweight.userdev)
}

group = "live.minehub"

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly(project(":core"))
}