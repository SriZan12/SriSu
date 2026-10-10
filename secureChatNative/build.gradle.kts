plugins { id("com.android.library") }

android {
    namespace = "com.srisu.srisu.securechat"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation("org.signal:libsignal-client:0.105.0")
    implementation("org.signal:libsignal-android:0.105.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.6")
}
