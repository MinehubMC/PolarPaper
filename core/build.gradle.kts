plugins {
    id("java")

    alias(libs.plugins.paperweight.userdev)
}

group = "live.minehub"

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("${libs.versions.minecraft.get()}.build.+")
    compileOnly(libs.zstd)
}