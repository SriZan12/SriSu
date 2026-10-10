pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
rootProject.name = "SriSuE2eeSpike"
if (providers.gradleProperty("withAndroid").orNull == "true") include(":android")
