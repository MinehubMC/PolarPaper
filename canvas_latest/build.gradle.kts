plugins {
    id("java")

    alias(libs.plugins.weaver.userdev)
}

group = "live.minehub"

repositories {
    mavenCentral()
    maven("https://maven.canvasmc.io/releases")
}

dependencies {
    paperweight.canvasDevBundle("${libs.versions.minecraft.get()}.build.+")
    compileOnly(project(":core"))
}