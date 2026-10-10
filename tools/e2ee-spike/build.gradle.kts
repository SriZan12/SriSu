plugins { java }

repositories {
    maven { url = uri("https://build-artifacts.signal.org/libraries/maven/"); content { includeGroup("org.signal") } }
    mavenCentral()
}

java { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
dependencies {
    testImplementation("org.signal:libsignal-client:0.105.0")
    testImplementation("junit:junit:4.13.2")
}
tasks.test { testLogging { events("passed", "failed", "skipped") } }
