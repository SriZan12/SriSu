import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val localProjectProperties = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    .asText
    .map { text -> Properties().apply { text.reader().use(::load) } }
val localApiBaseUrl = localProjectProperties.map {
    it.getProperty("srisu.apiBaseUrl").orEmpty().ifBlank { "http://127.0.0.1:8000/" }
}
val localApiEnvironment = localProjectProperties.map {
    it.getProperty("srisu.environment").orEmpty().ifBlank { "development" }
}
val apiBaseUrl = providers.gradleProperty("srisu.apiBaseUrl")
    .orElse(providers.environmentVariable("SRISU_API_BASE_URL"))
    .orElse(localApiBaseUrl)
    .orElse("http://127.0.0.1:8000/")
val apiEnvironment = providers.gradleProperty("srisu.environment")
    .orElse(providers.environmentVariable("SRISU_ENVIRONMENT"))
    .orElse(localApiEnvironment)
    .orElse("development")
val environmentSources = layout.buildDirectory.dir("generated/srisuEnvironment/commonMain")
abstract class GenerateEnvironment : DefaultTask() {
    @get:Input abstract val baseUrl: Property<String>
    @get:Input abstract val environment: Property<String>
    @get:OutputDirectory abstract val destination: DirectoryProperty
    @TaskAction fun generate() {
        val url = URI(baseUrl.get())
        require(url.scheme in listOf("http", "https") && url.host != null && url.userInfo == null && url.query == null && url.fragment == null)
        require(url.path == "/") { "srisu.apiBaseUrl must be an origin ending in /" }
        require(environment.get() in listOf("development", "staging", "production"))
        require(environment.get() == "development" || url.scheme == "https")
        val file = destination.get().file("com/srisu/srisu/core/config/BuildEnvironment.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package com.srisu.srisu.core.config\ninternal object BuildEnvironment { const val baseUrl = \"${url.toASCIIString()}\"; const val development = ${environment.get() == "development"} }\n")
    }
}
abstract class ValidateReleaseEnvironment : DefaultTask() {
    @get:Input abstract val baseUrl: Property<String>
    @get:Input abstract val environment: Property<String>
    @TaskAction fun validate() {
        require(environment.get() != "development" && baseUrl.get().startsWith("https://")) {
            "Release requires -Psrisu.environment=staging|production and an HTTPS -Psrisu.apiBaseUrl."
        }
    }
}
val generateEnvironment by tasks.registering(GenerateEnvironment::class) {
    baseUrl.set(apiBaseUrl)
    environment.set(apiEnvironment)
    destination.set(environmentSources)
}
val validateReleaseEnvironment by tasks.registering(ValidateReleaseEnvironment::class) {
    baseUrl.set(apiBaseUrl)
    environment.set(apiEnvironment)
}

room { schemaDirectory("$projectDir/schemas") }

kotlin {
    val xcf = XCFramework()
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "com.srisu.srisu.ComposeApp")
            xcf.add(this)
        }
    }
    
    sourceSets {
        commonMain { kotlin.srcDir(environmentSources) }
        
        androidMain.dependencies {
            // Recovered media preparation/camera components; same versions as b0421d4.
            implementation("androidx.media3:media3-muxer:1.10.1")
            implementation("androidx.media3:media3-container:1.10.1")
            implementation("androidx.camera:camera-camera2:1.5.3")
            implementation("androidx.camera:camera-lifecycle:1.5.3")
            implementation("androidx.camera:camera-view:1.5.3")
            implementation(libs.androidx.core.splashscreen)
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.ui.tooling)
//            implementation(libs.koin.android)
//            implementation(libs.koin.androidx.compose)
            implementation(libs.androidx.foundation.android)
            implementation (libs.androidx.paging.compose)
            implementation (libs.androidx.paging.runtime)
            implementation(libs.androidx.material.icons.extended.android)


        }
        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.trixnity.client)
            implementation(libs.trixnity.client.cryptodriver.vodozemac)
            implementation(libs.trixnity.client.media.okio)
            implementation(libs.trixnity.client.repository.room)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)


            // accompanist permissions.

            implementation(libs.accompanist.permissions)

            // ktor dependency
            implementation(libs.bundles.ktor)
            implementation(libs.bundles.coil)
//            implementation(libs.ktor.client.cio)

            //serialization
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)

//            Logger - Napier
            implementation(libs.napier)

//            Wheel DateTime picker
            implementation(libs.kmp.date.time.picker)
            implementation(libs.kotlinx.datetime)

//            koin
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)

//            KVault
            implementation(libs.kvault)

            //DataStore
            implementation(libs.datastore.preferences)
            implementation(libs.atomicfu)

            //type-safe navaigation
            implementation(libs.navigation.compose)

            // ui-backhandler
            implementation(libs.ui.backhandler)

            //paging
            implementation(libs.paging.compose.common)
            implementation(libs.paging.common)

            //material-icons
            implementation(libs.material.icons.core)

            //swipeable compo
//            implementation(libs.swipeable.kmp)


        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            implementation("io.ktor:ktor-client-mock:3.5.2")
        }

        androidUnitTest.dependencies {
            implementation(libs.kotlin.testJunit)
            implementation("org.robolectric:robolectric:4.16.1")
            implementation("androidx.compose.ui:ui-test-junit4:1.9.0")
        }

        androidInstrumentedTest.dependencies {
            implementation("androidx.test:runner:1.6.2")
            implementation("androidx.test.ext:junit:1.2.1")
        }

        iosTest.dependencies {
            // Test-only: renders chat states on the iOS simulator for screenshot evidence.
            // Matches the Compose UI version resolved transitively for iOS (1.10.2), not the plugin's 1.8.2.
            implementation("org.jetbrains.compose.ui:ui-test:1.10.2")
        }

        iosMain.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.darwin)

        }
    }
}

android {
    testOptions { unitTests.isIncludeAndroidResources = true }
    namespace = "com.srisu.srisu"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        applicationId = "com.srisu.srisu"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("debug") { manifestPlaceholders["srisuCleartext"] = "true" }
        getByName("release") {
            manifestPlaceholders["srisuCleartext"] = "false"
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    debugImplementation(compose.uiTooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.0")
}

tasks.configureEach {
    if (name.startsWith("compile") || name.startsWith("ksp")) dependsOn(generateEnvironment)
    if ((name.startsWith("assemble") || name.startsWith("bundle") || name.startsWith("link")) && name.contains("Release")) {
        dependsOn(validateReleaseEnvironment)
    }
}

val integrationRun = providers.gradleProperty("srisu.coreIntegrationRun")
tasks.withType<Test>().configureEach {
    inputs.property("coreIntegrationRun", integrationRun.orElse("disabled"))
}
