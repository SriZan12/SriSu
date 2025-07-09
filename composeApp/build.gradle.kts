import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.ui.tooling)
//            implementation(libs.koin.android)
//            implementation(libs.koin.androidx.compose)
            implementation(libs.androidx.foundation.android)
            implementation (libs.androidx.paging.compose)
            implementation (libs.androidx.paging.runtime)

        }
        commonMain.dependencies {
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
            implementation(libs.ktor.client.cio)

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
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)
            implementation(libs.lifecycle.viewmodel)

//            KVault
            implementation(libs.kvault)

//            mokko
//            api(libs.moko.permissions.compose)
//            api(libs.moko.permissions)
//            implementation(libs.lifecycle.viewmodel.compose)

            //DataStore
            implementation(libs.datastore.preferences)
            implementation(libs.atomicfu)

            //type-safe navaigation
            implementation(libs.navigation.compose)

            //shimmer
            implementation(libs.accompanist.placeholder.material3)

            // ui-backhandler
            implementation(libs.ui.backhandler)


            //paging
            implementation(libs.paging.compose.common)
            implementation(libs.paging.common)

            //material-icons
            implementation(libs.material.icons.core)

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.darwin)

        }
    }
}

android {
    namespace = "com.srisu.srisu"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
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
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

