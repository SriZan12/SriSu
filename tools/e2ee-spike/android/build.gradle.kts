plugins { id("com.android.application") version "8.12.2" }

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://build-artifacts.signal.org/libraries/maven/"); content { includeGroup("org.signal") } }
}

android {
    namespace = "com.srisu.spike"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.srisu.spike"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "evaluation"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk.abiFilters += "x86_64"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    sourceSets.getByName("androidTest").java.srcDir("../src/test/java")
    // Test the exact production adapter as well as the initial library spike.
    sourceSets.getByName("main").java.srcDir("../../../secureChatNative/src/main/java")
    packaging.resources.excludes += setOf("**/*.dll", "**/*.dylib", "**/*.so")
    packaging.jniLibs.excludes += "**/libsignal_jni_testing.so"
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.6")
    implementation("org.signal:libsignal-client:0.105.0")
    implementation("org.signal:libsignal-android:0.105.0")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
