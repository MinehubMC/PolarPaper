rootProject.name = "polarpaper"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.canvasmc.io/public")
    }
}

include("core")
include("paper_latest")
include("paper_26_1_2")
include("paper_1_21_11")
include("canvas_latest")