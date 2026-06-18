plugins {
    id("java")

    alias(libs.plugins.paperweight.userdev)
}

group = "live.minehub"

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("26.1.2.build.+")
    compileOnly(project(":core"))
}