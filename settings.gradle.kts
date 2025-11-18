pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "ipitch-v2"

include("common")
include("proposal-manager")
include("proposal-view-manager")
include("interaction-manager")
include("user-manager")
