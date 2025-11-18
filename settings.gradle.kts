pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "ipitch-v2"

include("common")
include("ai-integration")
include("proposal-manager")
include("proposal-view-manager")
include("interaction-manager")
