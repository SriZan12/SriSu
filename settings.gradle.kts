rootProject.name = "SriSu"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // Hosted CI may share a Maven Central rate limit. Keep local resolution unchanged.
        if (providers.environmentVariable("SRISU_CI_MAVEN_MIRROR").orNull == "true") {
            maven("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // Hosted CI may share a Maven Central rate limit. Keep local resolution unchanged.
        if (providers.environmentVariable("SRISU_CI_MAVEN_MIRROR").orNull == "true") {
            maven("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
    }
}

include(":composeApp")
